"""Authoritative card-driven voice prompts.

A new server card MUST produce a real GPT-Live speech request, not merely an
append_instructions side effect. Failed/busy attempts are retried, but prompts
from previous cards can never be replayed after the server advances again.
No network calls, new models or extra sessions: uses the existing AgentSession.
"""
from __future__ import annotations

import asyncio
import time
from typing import Callable


class CardPromptCoordinator:
    def __init__(self, *, generate: Callable, state: Callable,
                 is_current: Callable, spoke_at: Callable,
                 log: Callable | None = None,
                 attempts: int = 3, sleep: Callable = asyncio.sleep):
        self.generate = generate
        self.state = state
        self.is_current = is_current
        self.spoke_at = spoke_at
        self.log = log or (lambda *_: None)
        self.attempts = attempts
        self.sleep = sleep
        self._task: asyncio.Task | None = None
        self._key = None

    @property
    def busy(self) -> bool:
        return self._task is not None and not self._task.done()

    def schedule(self, key: tuple, instructions: str) -> None:
        if not self.is_current(key):
            return
        if key == self._key and self.busy:
            return
        if self._task is not None and not self._task.done():
            self._task.cancel()
        self._key = key
        # Log numeric revision ONLY; never the secret word in production.
        self.log("card_prompt_queued", int(key[3]) if len(key)>3 else 0,
                 "new_card")
        self._task = asyncio.create_task(self._run(key, instructions))

    async def close(self) -> None:
        if self._task is not None and not self._task.done():
            self._task.cancel()
            try:
                await self._task
            except asyncio.CancelledError:
                pass

    async def _run(self, key: tuple, instructions: str) -> None:
        baseline = self.spoke_at()
        for attempt in range(self.attempts):
            if not self.is_current(key):
                return
            # Give a teammate a SHORT chance to finish a spoken guess.
            # Never discard the prompt just because the peer is speaking,
            # and do not wait 11 seconds in a sixty-second game.
            waiting_since = time.monotonic()
            while self.is_current(key):
                agent_state, peer_state = self.state()
                if self.spoke_at() > baseline:
                    return  # The model has already begun a valid response.
                busy_agent = agent_state in ("speaking", "thinking", "initializing")
                busy_peer = peer_state == "speaking"
                cap = 1.0 if busy_agent else 0.6
                if not (busy_agent or busy_peer) or time.monotonic() - waiting_since >= cap:
                    break
                await self.sleep(0.12)
            if not self.is_current(key):
                return
            try:
                self.log("card_prompt_request", attempt + 1, "start")
                handle = self.generate(instructions)
                if handle is None:
                    raise RuntimeError("generate_reply did not return a request")
                try:
                    await asyncio.wait_for(asyncio.shield(handle), timeout=4.5)
                except asyncio.TimeoutError:
                    if self.spoke_at() > baseline or self.state()[0] == "speaking":
                        return
                    raise RuntimeError("speech did not start within 4.5s")
                if handle.exception() is not None:
                    raise RuntimeError(type(handle.exception()).__name__)
                self.log("card_prompt_accepted", attempt + 1, "ok")
                return
            except asyncio.CancelledError:
                raise
            except Exception as exc:
                self.log("card_prompt_retry", attempt + 1, type(exc).__name__)
                if self.spoke_at() > baseline or not self.is_current(key):
                    return
                if attempt + 1 < self.attempts:
                    await self.sleep(0.25 * (attempt + 1))
        if self.is_current(key):
            self.log("card_prompt_exhausted", self.attempts, "no_audio")
