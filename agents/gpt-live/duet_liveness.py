"""Bounded anti-deadlock control for a LIVE pair of GPT-Live HOT-HAT agents.

Pure logic: no network or transcript. The worker updates it from real
LiveKit speech-state events and authoritative server turn snapshots.
"""
from __future__ import annotations

from dataclasses import dataclass


@dataclass
class DuetLiveness:
    last_activity: float
    last_voice: float = 0.0
    last_peer_voice: float = 0.0
    last_prompt: float = 0.0
    card_key: tuple | None = None
    prompts_without_voice: int = 0
    pending: bool = False
    max_prompts_without_voice: int = 3

    def mark_spoke(self, now: float) -> None:
        self.last_voice = now
        self.last_activity = now
        self.prompts_without_voice = 0

    def mark_heard(self, now: float) -> None:
        self.last_peer_voice = now
        self.last_activity = now
        self.prompts_without_voice = 0

    def card_changed(self, turn_id: str, role: str, word: str | None,
                     now: float) -> None:
        key = (turn_id, role, word if role == "explainer" else None)
        if key != self.card_key:
            self.card_key = key
            self.last_activity = now
            self.last_prompt = 0.0
            self.prompts_without_voice = 0
            self.pending = False

    def decide(self, *, now: float, epoch_ms: int, phase: str,
               role: str, word: str | None, paused: bool, deadline_ms: int,
               agent_state: str, user_state: str) -> str | None:
        # Strictly no voice or LLM requests outside an ACTIVE, paid game turn.
        if phase != "active" or paused or role not in ("explainer", "guesser"):
            return None
        if deadline_ms <= epoch_ms + 5500:
            return None
        if role == "explainer" and not word:
            return None
        if agent_state in ("speaking", "initializing"):
            return None
        if user_state == "speaking" or self.pending:
            return None
        # The explainer moves first. The guesser waits longer to avoid a
        # simultaneous prompt when both bots briefly stop talking.
        # After hearing a wrong guess the explainer should recover faster.
        # The other agent's actual "speaking" state still blocks interruption.
        # Keep the longer initial delay so startup is not double-prompted.
        delay = (4.0 if self.last_peer_voice > self.last_voice else 5.5
                 ) if role == "explainer" else 7.5
        # Thinking forever without publishing speech is another deadlock:
        # gently interrupt the stale generation after 12 seconds and ask
        # for one short next action, never while actual audio is playing.
        if agent_state == "thinking":
            delay = max(delay, 12.0)
        if now - self.last_activity < delay or (self.last_prompt > 0 and now - self.last_prompt < 7.5):
            return None
        if self.prompts_without_voice >= self.max_prompts_without_voice:
            return "failed"
        self.prompts_without_voice += 1
        self.last_prompt = now
        self.last_activity = now
        self.pending = True
        action = ("give_hint" if role == "explainer" else
                  "make_guess" if self.last_peer_voice > 0 else "request_hint")
        return "reset_" + action if agent_state == "thinking" else action
