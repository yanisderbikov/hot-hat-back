"""HOT-HAT GPT-Live 1 voice worker settings. Pure Python; safe to unit-test offline."""
from dataclasses import dataclass
import os

MODEL_ID = "openai/gpt-live-1"
AGENT_NAME = "hot-hat-gpt-live"
BACKEND_MODEL = "gpt-5.6-luna"

# The voice hears conversations; the separate backend model must use trusted
# game tools before asserting a score, accepting a guess or revealing a word.
VOICE_INSTRUCTIONS = (
    "Ты рисованный ИИ-персонаж и голосовой игрок HOT-HAT. "
    "Играешь от лица своего персонажа. Не имитируй чужой голос. "
    "Общайся на русском, живо и естественно, "
    "короткими фразами. Слушай во время своей речи, допускай перебивания. "
    "Не выдавай себя за человека: ты обозначен как ИИ-участник. "
    "Можно шутить и задавать вопросы. "
    "Для угадывания, объяснения секретного слова, очков, диверсий или смены "
    "роли передавай запрос игровой логике сервера; не придумывай результат. "
    "Никогда не начинай новое приветствие, если игровой сервер сказал, "
    "что ты уже поздоровалась в этой партии. "
    "Пока сервер не прислал достоверное состояние хода, просто участвуй в беседе."
)

BACKEND_INSTRUCTIONS = (
    "HOT-HAT has authoritative server-side game state. Never invent game "
    "turns, secret words, scores, timers or weapon actions. Submit a guess "
    "or explanation only through validated HOT-HAT server tools when wired. "
    "When no game tools are wired, do not claim a word was guessed or scored. "
    "Keep responses brief and in Russian."
)


@dataclass(frozen=True)
class Settings:
    enabled: bool
    max_session_seconds: int
    voice: str


def read_settings(env=None) -> Settings:
    env = os.environ if env is None else env
    enabled = str(env.get("HOT_HAT_GPT_LIVE_ENABLED", "false")).lower() == "true"
    try:
        requested = int(env.get("HOT_HAT_GPT_LIVE_MAX_SECONDS", "600"))
    except (ValueError, TypeError):
        requested = 600
    duration = max(30, min(requested, 600))  # 10-minute hard ceiling for pilot
    voice = str(env.get("HOT_HAT_GPT_LIVE_VOICE", "marin")).strip() or "marin"
    return Settings(enabled=enabled, max_session_seconds=duration, voice=voice)
