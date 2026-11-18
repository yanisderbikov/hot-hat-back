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
    # A guesser only gets ONE prompted turn per newly heard explainer cue.
    # Otherwise the watchdog fires every few seconds and the voice model
    # compulsively repeats "нужна новая подсказка" with no new evidence.
    peer_voice_at_last_prompt: float = -1.0
    hint_requests_this_card: int = 0
    max_prompts_without_voice: int = 3
    # Only TWO authentic AI participants use the shorter idle delays.
    # Human+AI keeps the previously tested 5.5/7.5-second logic.
    fast_duet: bool = False
    # Opt-in fast cadence for LIVE 60-second match. Historical default
    # remains unchanged for replay compatibility and standalone tests.
    speed_round: bool = False
    # Production-only explainer initiative. Tests and ordinary listening
    # modes retain their historic delays unless explicitly opted in.
    continuous_explainer: bool = False

    def mark_spoke(self, now: float) -> None:
        self.last_voice = now
        self.last_activity = now
        self.prompts_without_voice = 0

    def mark_heard(self, now: float) -> None:
        self.last_peer_voice = now
        self.last_activity = now
        self.prompts_without_voice = 0

    def card_changed(self, turn_id: str, role: str, word: str | None,
                     now: float, revision: int = 0) -> None:
        key = (turn_id, role, word if role == "explainer" else None)
        if revision and role == "explainer":
            key += (revision,)
        if key != self.card_key:
            self.card_key = key
            self.last_activity = now
            self.last_prompt = 0.0
            self.prompts_without_voice = 0
            self.pending = False
            self.peer_voice_at_last_prompt = -1.0
            self.hint_requests_this_card = 0

    def decide(self, *, now: float, epoch_ms: int, phase: str,
               role: str, word: str | None, paused: bool, deadline_ms: int,
               agent_state: str, user_state: str) -> str | None:
        # Strictly no voice or LLM requests outside an ACTIVE, paid game turn.
        if phase != "active" or paused or role not in ("explainer", "guesser"):
            return None
        # A 60-second round is not finished with five seconds left.
        # Keep responding until the final ~2 seconds; the old 5.5s guard
        # silenced bots during exactly the window users need a final hint.
        if deadline_ms <= epoch_ms + 2000:
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
        if self.speed_round:
            if self.fast_duet:
                # Genuine AI+AI must keep the ball moving: quick follow-up
                # after a wrong guess without VAD-triggered interruption.
                delay = (1.2 if self.last_peer_voice > self.last_voice else 2.3
                         ) if role == "explainer" else 2.6
                thinking_delay = 5.0
                cooldown = 2.8
            else:
                # Human+AI: the explainer owns the initiative. Do not require
                # a human to ask for a hint every new word or after a guess.
                delay = (2.0 if self.last_peer_voice > self.last_voice else 3.2
                         ) if role == "explainer" else 5.0
                thinking_delay = 6.0
                cooldown = 3.0
        elif self.fast_duet:
            delay = (2.6 if self.last_peer_voice > self.last_voice else 3.6
                     ) if role == "explainer" else 4.2
            thinking_delay = 8.0
            cooldown = 5.5
        else:
            delay = (4.0 if self.last_peer_voice > self.last_voice else 5.5
                     ) if role == "explainer" else 7.5
            thinking_delay = 12.0
            cooldown = 7.5
        if self.continuous_explainer and role == "explainer":
            # In a 60-second word race, 3.2s solo idle + 1.35s tick is
            # a painful multi-second pause after EACH clue. Keep offering
            # new hints when the partner is silent, but never while either
            # participant is actually speaking (guards above).
            delay = 0.9 if self.last_peer_voice > self.last_voice else 1.7
            cooldown = 2.2 if self.fast_duet else 2.5
        # Only reset an actually stuck thinking state, not speaking audio.
        if agent_state == "thinking":
            delay = max(delay, thinking_delay)
        if now - self.last_activity < delay or (self.last_prompt > 0 and now - self.last_prompt < cooldown):
            return None
        # Do not force a second guess/question on the SAME unheard clue.
        # The explainer, not a robotic timer, must provide the next detail.
        if role == "guesser":
            if (self.peer_voice_at_last_prompt >= 0 and
                    self.last_peer_voice <= self.peer_voice_at_last_prompt):
                return None
            if self.last_peer_voice <= 0 and self.hint_requests_this_card:
                return None
        if self.prompts_without_voice >= self.max_prompts_without_voice:
            return "failed"
        self.prompts_without_voice += 1
        self.last_prompt = now
        self.last_activity = now
        self.pending = True
        action = ("give_hint" if role == "explainer" else
                  "make_guess" if self.last_peer_voice > 0 else "request_hint")
        if role == "guesser":
            self.peer_voice_at_last_prompt = self.last_peer_voice
            if action == "request_hint":
                self.hint_requests_this_card += 1
        return "reset_" + action if agent_state == "thinking" else action
