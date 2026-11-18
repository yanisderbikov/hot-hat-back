"""Authoritative card-driven voice prompts.

A new server card MUST produce a real GPT-Live speech request, not merely an
append_instructions side effect. Failed/busy attempts are retried, but prompts
from previous cards can never be replayed after the server advances again.
No network calls, new models or extra sessions: uses the existing AgentSession.
"""
from __future__ import annotations

import asyncio
import time
from typing import Callable, Awaitable


class CardPromptCoordinator:
    def __init__(self, *, generate: Callable, state: Callable,
                 is_current: Callable, spoke_at: Callable,
                 log: Callable | None = None,
                 attempts: int = 3, sleep: Callable = asyncio.sleep,
                 commentary: Callable | None = None,
                 reset: Callable[[], Awaitable] | None = None,
                 require_voice: bool = False,
                 response_timeout: float = 3.0,
                 commentary_grace: float = 1.2):
        self.generate = generate
        self.state = state
        self.is_current = is_current
        self.spoke_at = spoke_at
        self.log = log or (lambda *_: None)
        self.attempts = attempts
        self.sleep = sleep
        self.commentary = commentary
        self.reset = reset
        self.require_voice = require_voice
        self.response_timeout = response_timeout
        # Keep native real-time commentary first; the optional shorter
        # SOLO delay gets an explicit reply going before ten seconds pass.
        self.commentary_grace = max(0.15, min(1.5, commentary_grace))
        self._task: asyncio.Task | None = None
        self._key = None
        self._last_update_at = 0.0
        # GPT-Live generate_reply can remain pending for 10 seconds. The
        # coordinator must remember it even if the old card task is cancelled.
        self._pending_handle = None
        self._superseding_key = None
        # A verified score is not complete until the NEXT card has actually
        # started speaking. Keep one bounded, native-context recovery task
        # per authoritative card without scheduling competing model replies.
        self._score_silence_task: asyncio.Task | None = None

    @property
    def busy(self) -> bool:
        return self._task is not None and not self._task.done()

    def schedule(self, key: tuple, instructions: str, *,
                 after_verified_score: bool = False) -> None:
        if not self.is_current(key):
            return
        if key == self._key and self.busy:
            # The same word is still being explained. A finalized guess
            # changes what the NEXT hint must say, but cancelling an in-flight
            # realtime reply used to freeze GPT-Live. Deliver the new context
            # through native commentary WITHOUT a competing generate_reply.
            if (self.commentary is not None and
                    time.monotonic() - self._last_update_at >= 0.65):
                self._last_update_at = time.monotonic()
                try:
                    self.commentary(instructions)
                    self.log("card_prompt_updated", int(key[3]) if len(key)>3 else 0,
                             "same_card")
                except Exception as exc:
                    self.log("card_prompt_update_failed", 0,
                             type(exc).__name__)
            return
        if self._task is not None and not self._task.done():
            self._task.cancel()
        if key != self._key and self._score_silence_task is not None:
            self._score_silence_task.cancel()
            self._score_silence_task = None
        if key != self._key and self._pending_handle is not None:
            # GPT-Live explicitly supports superseding an unfinished
            # generate_reply with a NEW request. Keeping the old handle
            # blocked all next-card speech for up to 10 seconds.
            # Only an AUTHORITATIVE card change can supersede a request:
            # same-card repeated guesses remain serialized.
            self.log("card_prompt_supersede", int(key[3]) if len(key)>3 else 0,
                     "new_authoritative_card")
            self._pending_handle = None
            self._superseding_key = key
        else:
            self._superseding_key = None
        self._key = key
        self._last_update_at = 0.0
        # Log numeric revision ONLY; never the secret word in production.
        self.log("card_prompt_queued", int(key[3]) if len(key)>3 else 0,
                 "new_card")
        self._task = asyncio.create_task(self._run(key, instructions))
        if after_verified_score and self.commentary is not None:
            # Capture the pre-card voice timestamp. A real new-card voice
            # event makes this recovery a no-op.
            self._score_silence_task = asyncio.create_task(
                self._recover_after_score(key, self.spoke_at()))

    def react_to_guess(self, key: tuple, instructions: str) -> bool:
        """One contextual voice follow-up, without competing generate_reply.

        A same-card incorrect guess is a NEW conversation event, not a new
        secret card. Never replace the in-flight provider reply for it.
        Returning False allows the caller to use native commentary when
        the first card has only been primed (no coordinator key yet).
        """
        if key != self._key or not self.is_current(key):
            return False
        if self.commentary is None:
            return False
        now = time.monotonic()
        if now - self._last_update_at < 0.45:
            return True
        self._last_update_at = now
        try:
            self.commentary(instructions)
            self.log("card_prompt_duet_reaction",
                     int(key[3]) if len(key) > 3 else 0, "native")
            return True
        except Exception as exc:
            self.log("card_prompt_duet_reaction_failed",
                     0, type(exc).__name__)
            return False

    async def _recover_after_score(self, key: tuple, baseline: float) -> None:
        """Give a stalled full-duplex model ONE extra native voice cue.

        GPT-Live's explicit generate_reply is advisory and can refuse after
        10 seconds. A verified card change must not rely exclusively on its
        handle finishing: native commentary can re-engage the model without
        interrupting a delegated turn or creating a second generate_reply.
        """
        try:
            await self.sleep(2.4)
            while self.is_current(key) and self._key == key:
                if self.spoke_at() > baseline:
                    return
                agent_state, peer_state = self.state()
                # The model can still be playing the PREVIOUS card while
                # agent_state remains "speaking" (no new speech onset event).
                # The OLD guard silently discarded recovery exactly then.
                # An updated native commentary cue is safe: it does not
                # interrupt playout or request another provider reply.
                if peer_state == "speaking":
                    # The human's next guess has priority. The nudge remains
                    # armed until they stop; never talk over a teammate.
                    await self.sleep(0.15)
                    continue
                self.commentary(
                    "Опиши этот предмет ещё одним коротким конкретным "
                    "признаком. Не называй загаданное слово.")
                self.log("card_prompt_score_silence_recovery",
                         int(key[3]) if len(key) > 3 else 0, "native_once")
                return
        except asyncio.CancelledError:
            return
        except Exception as exc:
            self.log("card_prompt_score_silence_recovery_failed",
                     0, type(exc).__name__)

    async def close(self) -> None:
        if self._score_silence_task is not None:
            self._score_silence_task.cancel()
            try:
                await self._score_silence_task
            except asyncio.CancelledError:
                pass
            self._score_silence_task = None
        if self._task is not None and not self._task.done():
            self._task.cancel()
            try:
                await self._task
            except asyncio.CancelledError:
                pass

    async def _run(self, key: tuple, instructions: str) -> None:
        baseline = self.spoke_at()
        # GPT-Live's append_commentary is a SPOKEN paraphrase channel,
        # not a safe place for repeated control commands. Schedule once
        # per fresh card; never inject timer/progress chatter.
        if self.commentary is not None and self.is_current(key):
            try:
                self.commentary(instructions)
                self.log("card_prompt_commentary", 1, "native")
                for _ in range(max(1, round(self.commentary_grace / 0.15))):
                    if not self.is_current(key):
                        return
                    if self.spoke_at() > baseline:
                        self.log("card_prompt_voice_started", 1, "commentary")
                        return
                    await self.sleep(0.15)
            except asyncio.CancelledError:
                raise
            except Exception as exc:
                self.log("card_prompt_commentary_failed", 1, type(exc).__name__)
        for attempt in range(self.attempts):
            if not self.is_current(key):
                return
            if self._pending_handle is not None:
                if not self._pending_handle.done():
                    # Native commentary has already updated the card context.
                    # Never start a competing response before the old reply
                    # has really ended inside the full-duplex provider.
                    self.log("card_prompt_yield_pending",attempt,"old_reply_in_flight")
                    return
                self._pending_handle = None
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
                elapsed = time.monotonic() - waiting_since
                if not (busy_agent or busy_peer):
                    break
                if (agent_state == "thinking" and not busy_peer
                        and key == self._superseding_key):
                    # Unlike a repeated same-card retry, a NEW server card
                    # may replace an old pending GPT-Live answer. The SDK
                    # explicitly ends the old request on a newer request.
                    break
                # Never generate a second response WHILE the teammate is
                # talking: the old 0.6s cap did exactly that and caused
                # repeated RuntimeError refusals after barge-in.
                if busy_peer:
                    if elapsed >= 2.5:
                        self.log("card_prompt_yield_peer", 0, "awaiting_silence")
                        return  # watchdog may retry once the partner pauses
                    await self.sleep(0.12)
                    continue
                # Native model already speaking: no second reply is needed.
                if agent_state == "speaking":
                    self.log("card_prompt_yield_voice", 0, "already_speaking")
                    return
                # Thinking / initializing can temporarily hold a prior turn.
                # Avoid overlapping speech requests; liveness can reset an
                # actually stuck thinking state later.
                if elapsed >= 2.5:
                    self.log("card_prompt_yield_busy", 0, agent_state[:24])
                    return
                await self.sleep(0.12)
            if not self.is_current(key):
                return
            # State can flip between the last poll and this generation.
            agent_state, peer_state = self.state()
            if (peer_state == "speaking" or
                    agent_state in ("speaking","initializing") or
                    (agent_state == "thinking" and key != self._superseding_key)):
                self.log("card_prompt_yield_busy", 0, "state_changed")
                return
            try:
                self.log("card_prompt_request", attempt + 1, "start")
                handle = self.generate(instructions)
                if handle is None:
                    raise RuntimeError("generate_reply did not return a request")
                self._pending_handle=handle
                if self._superseding_key == key:
                    self._superseding_key = None
                # An advisory generate_reply can stay pending for ten seconds
                # even while the native model is deciding whether to speak.
                # During this interval DON'T let the game go silent: keep
                # refreshing commentary every ~1.8s, without creating another
                # concurrent response or talking over the teammate.
                try:
                    deadline=time.monotonic()+self.response_timeout
                    next_nudge=time.monotonic()+1.8
                    while not handle.done() and self.is_current(key):
                        if (self.spoke_at() > baseline or
                                self.state()[0] == "speaking"):
                            return
                        now=time.monotonic()
                        if now >= deadline:
                            self.log("card_prompt_pending",attempt+1,
                                     "provider_not_finished")
                            return
                        if now >= next_nudge:
                            # append_commentary is SOMETHING THE MODEL MAY
                            # SAY ALOUD, not a private control channel.
                            # Earlier this branch injected "Время игры идёт",
                            # so the player heard "время капает" instead of
                            # a clue. Never send process or countdown text
                            # to a spoken commentary channel.
                            self.log("card_prompt_waiting",attempt+1,
                                     "provider_not_started")
                            next_nudge=now+1.8
                        await self.sleep(0.15)
                    if not self.is_current(key):
                        return
                    if handle.done():
                        await handle
                        if handle.exception() is not None:
                            raise RuntimeError(type(handle.exception()).__name__)
                finally:
                    if handle.done() and self._pending_handle is handle:
                        self._pending_handle=None
                if self.require_voice and self.spoke_at() <= baseline:
                    # A successful handle is not evidence that the full-duplex
                    # model actually produced any sound. Wait for an event.
                    for _ in range(4):
                        if self.spoke_at() > baseline or not self.is_current(key):
                            break
                        await self.sleep(0.12)
                    if self.spoke_at() <= baseline:
                        raise RuntimeError("reply finished without voice")
                self.log("card_prompt_accepted", attempt + 1, "voice" if self.require_voice else "ok")
                return
            except asyncio.CancelledError:
                raise
            except Exception as exc:
                self.log("card_prompt_retry", attempt + 1, type(exc).__name__)
                if self.spoke_at() > baseline or not self.is_current(key):
                    return
                if attempt + 1 < self.attempts:
                    # Do not interrupt and immediately hammer an autonomous
                    # full-duplex model. Give it an ordinary commentary nudge,
                    # then listen for actual voice before a new request.
                    # A second paraphrase instruction often becomes audible
                    # meta-talk and the model refuses the next overlapping
                    # response. Wait for this first provider request to
                    # finish, then use only a bounded legitimate retry.
                    for _ in range(10):
                        if self.spoke_at() > baseline or not self.is_current(key):
                            return
                        await self.sleep(0.15)
        if self.is_current(key):
            self.log("card_prompt_exhausted", self.attempts, "no_audio")
