"""Low-latency, private guidance for almost-correct compound-word guesses.

Only the EXPLAINER sees the secret. This module never scores words: the
server's exact-phrase matcher remains the sole authority for awarding points.
Never log the returned clue, transcript, or the secret.
"""
from __future__ import annotations

import re
import json
from dataclasses import dataclass

_WORDS = re.compile(r"[^\W_]+", re.UNICODE)
_FILLER = frozenset(("это", "ну", "а", "да", "нет", "может", "быть", "наверное",
                     "думаю", "кажется", "или", "что", "так", "же", "ли"))


def words(text: str) -> tuple[str, ...]:
    return tuple(_WORDS.findall(str(text or "").lower().replace("ё", "е")))


def guess_signature(text: str) -> tuple[str, ...]:
    """Identifies repeated spoken guesses without retaining transcript text."""
    tokens = words(text)
    meaningful = tuple(t for t in tokens if t not in _FILLER)
    return meaningful or tokens


def _word_matches(target: str, spoken: str) -> bool:
    if target == spoken:
        return True
    # Russian noun case endings are only used to guide conversation, NEVER
    # to count an answer. Keep this conservative to avoid false near-matches.
    if len(target) < 5:
        return False
    stem = target[:-2] if target.endswith("ец") else target
    if target.endswith("ец"):
        return any(spoken == stem + ending for ending in ("ца", "цу", "цом", "це", "цы"))
    if target.endswith("а"):
        return spoken in (target[:-1] + "ы", target[:-1] + "е",
                          target[:-1] + "у", target[:-1] + "ой")
    if target.endswith("я"):
        return spoken in (target[:-1] + "и", target[:-1] + "ю",
                          target[:-1] + "ей")
    return False


@dataclass(frozen=True)
class PartialGuess:
    kind: str
    matched_indices: tuple[int, ...]

    def instruction(self, repeated: bool = False) -> str:
        if self.kind == "missing_modifier":
            if repeated:
                return (
                    "Напарник уже нашёл правильный предмет, но снова называет "
                    "его без недостающего признака. Не объясняй весь предмет "
                    "заново! Скажи коротко, что именно он понял правильно, "
                    "и дай ОДИН конкретный намёк только на недостающее "
                    "определение: состояние, способ приготовления, свойство "
                    "или разновидность. Не называй недостающие слова. "
                    "Не объявляй, что полный ответ угадан."
                )
            return (
                "Напарник назвал правильную основную часть составного ответа, "
                "но не произнёс обязательное уточнение. Сразу отреагируй "
                "естественно, например: «Да, близко! Но какой именно?» "
                "и коротко спроси о недостающем признаке. "
                "Не называй ни полный ответ, ни недостающее слово. "
                "Не произноси подтверждение полного зачёта."
            )
        if self.kind == "missing_object":
            return (
                "Напарник угадал только признак, но не сам предмет составного "
                "ответа. Быстро скажи: «Да, такой! А что именно?» "
                "и дай один образ предмета, НЕ называя его. "
                "Не объявляй полный ответ угаданным."
            )
        return (
            "В догадке напарника есть правильная часть составного ответа. "
            "Не начинай старую подсказку заново: подтвердить можно только "
            "часть, затем спроси о недостающей части или дай краткий новый "
            "признак. Не произноси секрет и не объявляй полный зачёт."
        )


def partial_compound_guess(secret: str, spoken: str) -> PartialGuess | None:
    """Return guidance only when a real compound card was PARTIALLY guessed.

    An exact complete answer is NEVER classified here (server scores it).
    A single-card word or a merely related root ("компас" vs "компост")
    cannot produce an undeserved verbal success.
    """
    target = words(secret)
    observed = words(spoken)
    if len(target) < 2 or not observed:
        return None
    matched = tuple(i for i, t in enumerate(target)
                    if len(t) >= 3 and any(_word_matches(t, s) for s in observed))
    if not matched or len(matched) == len(target):
        return None
    if matched == (len(target) - 1,):
        return PartialGuess("missing_modifier", matched)
    if matched == (0,):
        return PartialGuess("missing_object", matched)
    return PartialGuess("partial", matched)


DUET_VOICE_GUESS_TOPIC = "hot-hat.duet.guess.v1"


def verified_duet_voice_packet(payload: bytes, *, topic: str,
                              sender: str, expected_peer: str,
                              turn_id: str, revision: int) -> str | None:
    """Accept an own-voice transcript only from the authorized linked AI.

    The relay has NO secret and is not scoring authority; the explainer must
    still send the candidate through the authenticated backend's exact matcher.
    """
    if (topic != DUET_VOICE_GUESS_TOPIC or not expected_peer
            or sender != expected_peer or not turn_id
            or not isinstance(payload, bytes) or len(payload) > 800):
        return None
    try:
        row = json.loads(payload.decode("utf-8"))
        if (not isinstance(row, dict) or row.get("v") != 1 or
                row.get("turnId") != turn_id or
                type(row.get("cardRevision")) is not int or
                row.get("cardRevision") != revision):
            return None
        phrase = row.get("phrase")
        if (not isinstance(phrase, str) or len(phrase) > 180
                or not guess_signature(phrase)):
            return None
        return phrase.strip() or None
    except (ValueError, UnicodeError, TypeError):
        return None
