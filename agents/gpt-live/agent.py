"""HOT-HAT: explicit per-active-team GPT-Live 1 with server-authoritative game tools.

This worker NEVER joins a room automatically and never starts an inference
session unless HOT_HAT_GPT_LIVE_ENABLED=true AND a valid server-dispatched
session was created for the currently active unranked AI+human pair.
"""
from __future__ import annotations

import asyncio
import re
import inspect
import json
import logging
import os
import re
import time
from types import SimpleNamespace

from livekit import rtc
from livekit.plugins import silero
from livekit.agents import (
    Agent, AgentServer, AgentSession, JobContext, room_io,
    RunContext, cli, inference,
)
from livekit.agents.llm import function_tool

from config import (
    AGENT_NAME, BACKEND_INSTRUCTIONS, BACKEND_MODEL, MODEL_ID,
    VOICE_INSTRUCTIONS, DUET_VOICE_INSTRUCTIONS,
    DUET_BACKEND_INSTRUCTIONS, read_settings,
)
from game_client import GameClient
from duet_liveness import DuetLiveness
from card_prompts import CardPromptCoordinator
from guess_guidance import (
    CompoundTranscriptWindow, DUET_VOICE_GUESS_TOPIC,
    already_spoken_component_hint, guess_signature, partial_compound_guess,
    verified_duet_voice_packet,
)

logger = logging.getLogger("hot-hat-gpt-live")
server = AgentServer()


def _prewarm_vad(proc):
    """Preload local VAD before any time-limited game job starts.

    GPT-Live owns its turns. Without an explicit VAD, LiveKit cannot
    interrupt its audio playback as soon as the teammate starts speaking.
    Reusing a prewarmed model avoids startup latency on each new turn.
    """
    proc.userdata["vad"] = silero.VAD.load(
        min_speech_duration=0.12,
        min_silence_duration=0.35,
        activation_threshold=0.55,
    )


server.setup_fnc = _prewarm_vad


def _standalone_voice_approval(text: str) -> str | None:
    """Only unmistakable FINAL one-phrase spoken approvals can click a word.

    "Да, но какой?" acknowledges a partial answer and MUST NEVER score.
    The model's own spoken approval is still subject to the server's
    identity, card revision and game-turn validation.
    """
    import re as _re
    normalized = _re.sub(r"[^а-яa-z0-9 ]+", " ", str(text or "").lower().replace("ё", "е"))
    normalized = " ".join(normalized.split())
    approvals=(
        "да", "верно", "точно", "правильно", "есть",
        "угадал", "угадала", "именно", "это оно", "так и есть",
    )
    if normalized in approvals:
        return normalized
    # GPT-Live can repeat short unmistakable approvals inside one audio
    # turn ("Верно, верно!", "Да! Да!"). Final turn transcription arrives
    # AFTER speech; previous classifier rejected these and delayed scoring.
    # Never accept any non-approval word such as "но", "почти", "какой".
    words = normalized.split()
    if 1 < len(words) <= 3 and all(w in (
            "да", "верно", "точно", "правильно", "есть",
            "угадал", "угадала", "именно") for w in words):
        return words[0]
    # A full-duplex turn can contain "Да! <next sentence>" in the SAME
    # item. Treat the unequivocal first sentence as the approval, but
    # NEVER accept corrections like "Да! Но какой именно?".
    leading=_re.match(
        r"^\s*(да|верно|точно|правильно|есть|угадал|угадала|именно)"
        r"\s*[!.]\s+(.+)$",
        str(text or "").lower().replace("ё","е").strip())
    if leading and not _re.match(
            r"^(но|почти|близко|не|какой|что|скорее|может|возможно)\b",
            leading.group(2).strip()):
        return leading.group(1)
    return None


def _is_voice_confirmed_guess(row: dict) -> bool:
    """Only a server-confirmed voice recognition may cause a verbal 'Есть!'.

    Manual 'Угадали' clicks, scripted test actions and unknown clients must
    NEVER make the agent narrate score confirmation. All remain in memory.
    """
    return (isinstance(row, dict)
            and row.get("outcome") == "guessed"
            and str(row.get("actionId") or "").startswith("aiw_"))


def _voice_confirmation(role: str, server_matched: bool, ordinal: int) -> str | None:
    """Only the explaining bot can confirm a server-verified spoken guess.

    Keep the response diverse, one word, and free from any score claims.
    The calling path must be a genuine voice match, never a manual click.
    """
    if role != "explainer" or server_matched is not True:
        return None
    options = ("Есть!", "Точно!", "Верно!", "Да!", "Правильно!")
    return options[ordinal % len(options)]


def _history_prompt(rows: list[dict] | None) -> str:
    """Past completed words must NOT re-enter the active voice model."""
    safe = [row for row in (rows or []) if isinstance(row, dict)]
    guessed = sum(row.get("outcome") == "guessed" for row in safe)
    skipped = sum(row.get("outcome") == "skipped" for row in safe)
    return ("СТАТИСТИКА: угадано " + str(guessed)
            + ", пропущено " + str(skipped) + ". "
            "Прошлые карточки закрыты. НЕ вспоминай их слова: "
            "объясняй только новую, подтверждённую сервером карточку.")


def _human_delegated_explaining(phrase: str) -> bool:
    text = " ".join(str(phrase or "").lower().replace("ё", "е").split())
    return bool(re.search(
        r"(?:^|\b)(?:объясняй|объясни|начинай|начни|"
        r"давай ты|теперь ты|твоя очередь|твой ход|"
        r"ты (?:начинай|начинаешь|начни|объясняй|объясняешь|"
        r"объяснишь|объясни|будешь объяснять|будешь начинать|"
        r"будешь первым|первым будешь)|"
        r"можешь (?:объяснять|начинать|начать|объяснить))\b",text))


def _ai_committed_to_explaining(phrase: str) -> bool:
    text = " ".join(str(phrase or "").lower().replace("ё", "е").split())
    if re.search(r"\b(?:не буду|не хочу|не стану)\s+объяснять",text):
        return False
    return bool(re.search(
        r"(?:^|\b)(?:я (?:буду )?(?:объяснять|объясню|"
        r"объясняю|начну|начинаю)|давай я|я первый|"
        r"сейчас объясню|беру на себя|"
        r"(?:ладно|хорошо|окей|да),? (?:я )?начинаю)\b",text))


class HotHatPlayer(Agent):
    def __init__(self, game: GameClient, human_uid: str, *, smalltalk: bool = False,
                 should_greet: bool = False,
                 character_name: str = "Рублиша", personality: str = "",
                 duet_role: str = "", recent_words: list[dict] | None = None):
        self.game = game
        # Optional server-authoritative approval callback, registered only
        # for a live human+AI explainer in the worker. No guessed text or
        # secret is accepted as a model tool argument.
        self.confirm_last_human_guess = None
        self.human_uid = human_uid
        self.smalltalk = smalltalk
        self.duet_role = duet_role
        self.last_human_phrase = ""
        self.human_input_version = 0
        self.last_skipped_input_version = -1
        self.skip_lock = asyncio.Lock()
        self.suppress_next_word_commentary = False
        self.pending_voice_confirmation = None
        self.recent_word_keys = {
            str(row.get("actionId")) for row in (recent_words or [])
            if isinstance(row, dict) and row.get("actionId")
        }
        etiquette = ("" if duet_role else (
            " В этой партии приветствия от тебя ещё не было: можешь "
            "поздороваться один короткий раз. "
            if should_greet else
            " В этой партии уже было приветствие. Не здоровайся повторно, "
            "даже если подключился новый голосовой сеанс. "
        ))
        # Role negotiation was inherited from human+AI mode. For TWO voice
        # bots that conflicted with the server-assigned explainer/guesser role,
        # causing both to wait for each other or try to take the same job.
        if duet_role == "explainer":
            smalltalk_rules=(
                " ЭКСПЕРИМЕНТАЛЬНЫЙ ИИ-ДУЭТ. Сервер УЖЕ назначил тебя "
                "ОБЪЯСНЯЮЩИМ на весь ход. РОЛИ НЕ ОБСУЖДАЮТСЯ и НЕ МЕНЯЮТСЯ. "
                "Главная цель — МАКСИМУМ угаданных слов за минуту, а не беседа. "
                "При каждой новой карточке сразу дай ёмкую подсказку (3–9 слов): "
                "категория, назначение или заметный признак; не произноси "
                "секретное слово, его части и однокоренные формы САМ. "
                "ИСКЛЮЧЕНИЕ: если напарник УЖЕ произнёс одну часть "
                "составного ответа, можно повторить ТОЛЬКО названную им "
                "часть и спросить недостающее уточнение: «Да, но какой?» "
                "Недостающие части не раскрывай. "
                "Как только угадывающий начинает говорить, немедленно "
                "уступи ему слово, даже если не закончил подсказку. "
                "После неверной догадки немедленно дай НОВУЮ, более точную "
                "подсказку с другой стороны; не повторяй старую формулировку. "
                "Если напарник дал несколько неверных версий, меняй тактику: "
                "тип предмета, место, действие, сравнение или исключение. "
                "После верной догадки, подтверждённой СЕРВЕРОМ, только ты как "
                "ОБЪЯСНЯЮЩИЙ говоришь ОДНО короткое подтверждение: «Да!», "
                "«Точно!», «Верно!», «Есть!» или «Правильно!», меняй формулировку. "
                "СРАЗУ после этого новая подсказка без паузы, пересказа "
                "и объявления очков. Не подтверждай неверные догадки. "
                "Не задавай вопросов "
                "о ролях и не проси напарника объяснять. "
                "ОБЩЕЕ разрешение на шутки и болтовню в дуэте НЕ ДЕЙСТВУЕТ. "
                "Никаких вступлений, комментариев о процессе или длинных фраз. "
            )
        elif duet_role == "guesser":
            smalltalk_rules=(
                " ЭКСПЕРИМЕНТАЛЬНЫЙ ИИ-ДУЭТ. Сервер УЖЕ назначил тебя "
                "УГАДЫВАЮЩИМ на весь ход. РОЛИ НЕ ОБСУЖДАЮТСЯ и НЕ МЕНЯЮТСЯ. "
                "Твоя единственная задача — БЫСТРО УГАДЫВАТЬ слова. "
                "Как только понял ответ, НЕМЕДЛЕННО говори, даже если "
                "объясняющий ещё произносит подсказку. Старайся опережать "
                "длинные объяснения. Произноси ТОЛЬКО короткое слово "
                "или словосочетание, "
                "например: «Велосипед?» или «Почта?». "
                "Не начинай с «мне кажется», «может быть», «я думаю»; "
                "не повторяй подсказку, не рассуждай вслух, не комментируй "
                "свою догадку и не обсуждай правила, таймер, роли и счёт. "
                "Никаких «понял», «ага», «интересно», похвалы, благодарностей "
                "или шуток — это забирает время и перебивает объясняющего. "
                "На неверный ответ немедленно слушай новую подсказку и быстро "
                "предложи ДРУГУЮ догадку; не повторяй одну и ту же версию. "
                "Если после нескольких подсказок неясно, можно ОДИН РАЗ "
                "коротко уточнить категорию, например «Это предмет?». "
                "Если объясняющий надолго замолчал — коротко попроси "
                "«Ещё подсказку!», но не переспрашивай после каждой версии. "
                "После верной догадки замолчи и слушай следующую подсказку: "
                "подтверждение произносит ТОЛЬКО объясняющий, не ты. "
                "Не произноси «Да!», «Верно!», «Есть!» или похвалу в роли "
                "угадывающего. Не раскрывай секретное слово через инструменты и никогда "
                "не вызывай choose_to_explain. "
                "ОБЩЕЕ разрешение на шутки и болтовню в дуэте НЕ ДЕЙСТВУЕТ. "
            )
        else:
            smalltalk_rules = (
                " Ты в режиме smol talk с владельцем игры. Общайся непринуждённо "
            "по-дружески. Не угадывай слова, не выбирай роли, "
            "не объявляй очки и не используй игровые инструменты. "
            "Не мешай текущему ходу другой команды. "
        ) if smalltalk else (
                " Пока выбираете объясняющего, минута ЕЩЁ НЕ НАЧАЛАСЬ. "
                "Секретное слово недоступно; не выдумывай слова и подсказки. "
                " Сейчас ты играешь ровно с одним напарником, его голос "
                "единственный на твоём входе. В начале хода обсудите, кто "
                "объясняет. Когда партнёр говорит 'объясняй ты', НЕМЕДЛЕННО "
                "вызови choose_to_explain. Если партнёр сам начал объяснять, "
                "слушай, предлагай догадки голосом. Когда объясняешь ты, "
                "Никогда САМА не произноси секретное слово, "
                "его части или однокоренные формы. ИСКЛЮЧЕНИЕ: если "
                "напарник уже произнёс только часть составного ответа, "
                "можно повторить ТОЛЬКО эту ранее названную часть, "
                "сразу спросив недостающее уточнение. Чётко давай подсказки. "
                "Просьбы 'пропусти', 'давай след слово', 'следующее слово', "
                "'давай другое', 'давай следующее' — одинаковые команды "
                "пропуска текущей карточки. При них вызови skip_after_teammate_request. "
                "В игре ВСЕГДА реагируй на скорость: максимум одно-два "
                "слова подтверждения, без повторения угаданного слова, без "
                "лишних поздравлений, сразу переходи к следующей подсказке. "
                "Если человек НАЖАЛ кнопку 'Угадали', НЕ озвучивай это "
                "событие: он уже увидел подтверждение на экране. "
                "Если сервер подтвердил правильную голосовую догадку "
                "напарника, ты как ОБЪЯСНЯЮЩИЙ обязательно подтверждаешь "
                "одним коротким словом: «Да!», «Точно!», «Есть!», «Верно!» "
                "или «Правильно!», затем немедленно играешь дальше. "
                "Если ты УГАДЫВАЮЩИЙ, не подтверждай ответы. "
                "Когда ОБЪЯСНЯЕШЬ человеку, веди игру дружелюбно, "
                "изобретательно и естественно, как напарница, а не судья. "
                "После неверного варианта НЕ останавливайся: попробуй "
                "новый подход к тому же слову — действие, место, образ, "
                "аналогия, противоположность или ситуация из жизни. "
                "Не повторяй одну и ту же подсказку. Если пользователь "
                "близок к ответу, подведи дополнительным признаком, "
                "но НЕ произноси секретное слово сама. "
                "Никогда не говори вслух «не засчитали», «сервер», "
                "«система», «распознавание», «ошибка» или технические "
                "комментарии о том, как проверяется догадка. "
                "Пока карточка не сменилась, не заявляй, что её угадали. "
                "Если человек хочет ещё подсказку — отвечай сразу. "
                "Не меняй очки и таймер сама."
            )
        super().__init__(instructions=
                         "Тебя зовут " + character_name + ". " + personality + " "
                         + (DUET_VOICE_INSTRUCTIONS if duet_role
                            else VOICE_INSTRUCTIONS) + etiquette + smalltalk_rules
                         + " " + _history_prompt(recent_words))

    def prime_first_explainer_hint(self, state: dict) -> bool:
        """Feed the first card to an already answering GPT-Live model.

        Once choose_to_explain returns, GPT-Live is ALREADY composing its
        continuation to that tool result. Calling generate_reply in the same
        instant aborts both replies. Insert the secret as private thinking,
        give it an immediate clue request as commentary, and let the model
        continue its current reply naturally. The watcher must NOT issue a
        competing explicit response for this very first card.
        """
        word=state.get("currentWord")
        if state.get("role")!="explainer" or not word:
            return False
        self.duplex_session.append_thinking(
            "Теперь твоя роль ОБЪЯСНЯТЬ. Текущая карточка: " + str(word)
            + ". Это секрет: никогда не произноси само слово или его части.")
        self.duplex_session.append_commentary(
            "СРАЗУ объясняй этот предмет напарнику коротким признаком. "
            "После первой подсказки внимательно слушай догадку "
            "и сразу объясняй дальше новым конкретным признаком. "
            "Не называй ответ.")
        return True

    @function_tool
    async def choose_to_explain(self, context: RunContext) -> str:
        """Выбрать роль объясняющего и запустить минутный раунд, только
        когда напарник явно просит тебя объяснять слово."""
        if self.duet_role == "guesser":
            return "В ИИ-дуэте тебе назначено УГАДЫВАТЬ. Слушай подсказку!"
        # Claim the first voice prompt before the watcher can race us.
        self.suppress_next_word_commentary=True
        try:
            await self.game.choose_explainer()
            state=await self.game.context()
            if not self.prime_first_explainer_hint(state):
                self.suppress_next_word_commentary=False
            return "Роль назначена. Объясняй сразу."
        except Exception:
            self.suppress_next_word_commentary=False
            return "Роль могла уже смениться. Продолжай игру."

    async def skip_once(self, phrase: str, input_version: int) -> dict:
        # Final ASR and the LLM tool may react to the very same phrase.
        # Keep one skip per spoken utterance, without blocking later commands.
        async with self.skip_lock:
            if input_version == self.last_skipped_input_version:
                return {"skipped": False, "alreadyHandled": True}
            result = await self.game.skip(self.human_uid, phrase)
            if result.get("skipped"):
                self.last_skipped_input_version = input_version
                self.recent_word_keys.add(str(result.get("actionId") or ""))
            return result

    @function_tool
    async def skip_after_teammate_request(self, context: RunContext) -> str:
        """Пропустить карточку по просьбе напарника: 'пропусти',
        'давай след слово', 'следующее слово', 'давай другое'."""
        if not self.last_human_phrase:
            return "Сначала нужно услышать просьбу напарника."
        try:
            result = await self.skip_once(self.last_human_phrase, self.human_input_version)
            if result.get("alreadyHandled"):
                return "Это слово уже пропущено, продолжай игру."
            return ("Пропустили, продолжаем!"
                    if result.get("skipped") else
                    "Останься на текущем слове, предложи другую подсказку.")
        except Exception:
            return "Продолжи объяснять текущее слово."

    @function_tool
    async def confirm_correct_human_guess(self, context: RunContext) -> str:
        """Засчитать последнюю догадку человека, если она ТОЧНО верна.

        Вызывай этот инструмент ДО того, как произнести «Да», «Верно»
        или «Правильно» в роли объясняющего. Инструмент проверяет
        последнюю финальную голосовую догадку и текущую карточку.
        Не вызывай при частичном ответе, сомнении или неверной догадке.
        Не передавай в инструмент ответ или секретное слово.
        """
        callback = self.confirm_last_human_guess
        if callback is None or self.smalltalk or self.duet_role:
            return "Зачёт недоступен. Не подтверждай догадку."
        try:
            matched = await callback("верно", "tool")
        except Exception as exc:
            logger.warning("AI approval tool failed: %s", type(exc).__name__)
            return "Зачёт не подтверждён. Дай другую подсказку."
        if matched:
            return ("Слово засчитано. Скажи ОДНО короткое подтверждение "
                    "и сразу объясняй СЛЕДУЮЩУЮ карточку, когда она придёт. "
                    "Не повторяй подтверждение.")
        return ("Зачёт не подтверждён. НЕ говори «Да» или «Верно». "
                "Уточни загадку другим кратким признаком.")

    @function_tool
    async def read_game_role(self, context: RunContext) -> str:
        """Получить у сервера текущую роль, фазу хода и секретное слово
        только когда ты официально назначен объясняющим."""
        try:
            state = await self.game.context()
            if state.get("role") == "explainer":
                return ("Ты объясняешь. Секретное слово: " +
                        str(state.get("currentWord") or "нет") +
                        ". Не произноси секретное слово!")
            return ("Роль: " + str(state.get("role")) +
                    ". Секретное слово недоступно, слушай напарника.")
        except Exception:
            return "Этот ход уже неактивен."


def _model_usage(session: AgentSession, elapsed: float) -> tuple[float, int, int]:
    voice_seconds = 0.0
    input_tokens = 0
    output_tokens = 0
    usage = getattr(session, "usage", None)
    entries = getattr(usage, "model_usage", ()) if usage is not None else ()
    for record in entries or ():
        model = str(getattr(record, "model", "") or "").lower()
        if "gpt-live" in model:
            voice_seconds = max(voice_seconds, float(
                getattr(record, "session_duration", 0) or
                getattr(record, "duration", 0) or 0))
        else:
            input_tokens += int(getattr(record, "input_tokens", 0) or 0)
            output_tokens += int(getattr(record, "output_tokens", 0) or 0)
    # Only the actual connected session is metered. GPT-Live emits its usage
    # in approximately minute-sized chunks; the elapsed monotonic clock
    # supplies a conservative provisional estimate between those updates.
    return (min(180.0, max(voice_seconds, elapsed)),
            max(0, input_tokens), max(0, output_tokens))


async def accept_hot_hat_character(req) -> None:
    """Use the virtual room-player UID as the REAL LiveKit agent identity.
    This is essential for two AI teammates to subscribe to each other's audio
    without fragile name lookups or cross-team routing.
    """
    try:
        metadata = json.loads(getattr(req.job, "metadata", "") or "{}")
        bot_uid = str(metadata.get("botUid") or "")
        if not re.fullmatch(r"ai-gpt-live-[a-f0-9]{18}", bot_uid):
            await req.reject()
            return
        await req.accept(
            identity=bot_uid,
            name="ИИ-игрок",
            attributes={"hotHatAiUid": bot_uid, "hotHatKind": "hot-hat-gpt-live"},
        )
    except (ValueError, TypeError, KeyError):
        await req.reject()


@server.rtc_session(agent_name=AGENT_NAME, on_request=accept_hot_hat_character)
async def hot_hat_voice_player(ctx: JobContext) -> None:
    settings = read_settings()
    if not settings.enabled:
        logger.warning("Paid GPT-Live dispatch ignored: feature flag disabled")
        return

    try:
        metadata = json.loads(ctx.job.metadata or "{}")
        session_id = str(metadata["sessionId"])
        room_id = str(metadata["roomId"])
        bot_uid = str(metadata["botUid"])
        max_seconds = min(180, int(metadata.get("maxSessionSeconds", 180)))
        mode = "smalltalk" if metadata.get("mode") == "smalltalk" else "game"
        should_greet = metadata.get("shouldGreet") is True
        if not session_id.startswith("ai_") or not bot_uid.startswith("ai-gpt-live-"):
            raise ValueError("Unrecognized GPT-Live dispatch")
        if ctx.room.name != "hot-hat-" + room_id:
            raise ValueError("Wrong LiveKit game room")
    except (ValueError, KeyError, TypeError) as exc:
        logger.warning("Invalid GPT-Live dispatch metadata: %s", exc)
        return

    async with GameClient(session_id) as game:
        # Verify game number, team and participant BEFORE creating the paid
        # voice transport. A stale dispatch never consumes inference credits.
        try:
            initial = await game.context()
        except Exception as exc:
            logger.warning("Game session is no longer active: %s", exc)
            return
        if str(initial.get("botUid", "")) != bot_uid:
            return
        if str(initial.get("mode", "game")) != mode:
            logger.warning("GPT-Live character dispatch context mode mismatch")
            return
        human_uid = str(initial.get("humanUid") or "")
        character_name = str(initial.get("characterName") or "Рублиша")[:48]
        personality = str(initial.get("personality") or "")[:400]
        voice = str(initial.get("voice") or settings.voice)
        is_duo = initial.get("aiDuo") is True
        scripted_partner = initial.get("scriptedPartner") is True and mode == "game"
        designated_explainer_uid = str(initial.get("designatedExplainerUid") or "")
        if not human_uid:
            return

        await ctx.connect()
        if is_duo:
            # Both workers must be connected BEFORE either begins listening:
            # each receives ONLY the OTHER bot's audio (never itself or a
            # spectator). An unpaired worker exits without paying inference.
            for _ in range(60):
                if human_uid in ctx.room.remote_participants:
                    break
                await asyncio.sleep(0.2)
            else:
                logger.warning("AI duo teammate did not join: %s", human_uid)
                return
        # Not every LiveKit Cloud worker permits metadata updates. Use both
        # metadata and participant attributes, with AGENT-kind client fallback.
        for method, value in (
            ("set_metadata", json.dumps({
                "hotHatAiUid": bot_uid, "kind": "hot-hat-gpt-live",
            })),
            ("set_attributes", {"hotHatAiUid": bot_uid,
                                "hotHatKind": "hot-hat-gpt-live"}),
            ("set_name", character_name),
        ):
            try:
                setter = getattr(ctx.room.local_participant, method, None)
                if setter is not None:
                    result = setter(value)
                    if inspect.isawaitable(result):
                        await result
            except Exception as exc:
                logger.info("Agent identity %s unavailable: %s", method, type(exc).__name__)

        duo_role=("explainer" if bot_uid==designated_explainer_uid else "guesser") if is_duo else ""
        agent = HotHatPlayer(game, human_uid, smalltalk=(mode == "smalltalk"),
                             should_greet=should_greet, character_name=character_name,
                             personality=personality, duet_role=duo_role,
                             recent_words=initial.get("recentWords") or [])
        # SCRUM-52: return human+Rublisha to the original natural duplex
        # end-of-turn detection.  The 150/180ms thresholds were meant for
        # bot+bot speed rounds; applying them to a human fragmented speech
        # and caused false "unrecognized" / forced-answer loops.
        # Preserve low latency only for the explicitly two-AI duet.
        duet_turn_settings = ({
            "allow_interruptions": True,
            # Wait long enough to distinguish an actual spoken word from
            # mic/noise/VAD fragments, but still allow genuine barge-in.
            "min_interruption_duration": 0.20,
            # Short one-word guesses must be transcribed as complete units;
            # overly aggressive 0.35s endpointing split Russian guesses.
            "min_endpointing_delay": 0.48,
            "max_endpointing_delay": 1.25,
        } if is_duo else {})
        session = AgentSession(
            **duet_turn_settings,
            # GPT-Live controls its own model output, but framework VAD
            # immediately cuts LOCAL playback on real teammate speech.
            # Otherwise completed guesses are masked by the explainer.
            vad=ctx.proc.userdata["vad"],
            llm=inference.DuplexModel(
            model=MODEL_ID,
            voice=voice,
            extra_kwargs={"responses_options": {
                "model": BACKEND_MODEL,
                "instructions": (DUET_BACKEND_INSTRUCTIONS if is_duo
                                 else BACKEND_INSTRUCTIONS),
            }},
        ))
        stop = asyncio.Event()
        session.on("close", lambda *_: stop.set())
        latest = ""
        started = 0.0
        duet_bootstrap_word = None
        duet_bootstrap_turn = None
        duet_first_reply_task = None
        duet_watchdog_task = None
        # Human+AI explaining turns need anti-silence too: previously only
        # owner AI+AI duets received watchdog prompts.
        solo_explainer = mode == "game" and not is_duo and not scripted_partner
        watch_game_speech = is_duo or solo_explainer
        duet_live = DuetLiveness(
            time.monotonic(),
            max_prompts_without_voice=3 if is_duo else 8,
            fast_duet=is_duo,
            speed_round=True,
            continuous_explainer=True,
        )
        duet_snapshot = {}
        last_agent_spoke = 0.0
        # VAD speech onset must be separate from finalized ASR arrival.
        # mark_heard() updates last_peer_voice on BOTH events, so that value
        # is not a reliable speech-start timestamp for early AI approval.
        last_peer_speech_started_at = 0.0
        duet_wrong_guesses = 0
        duet_guess_verification_pending = False
        # Per-card partial answers are private to the active explainer.
        duet_partial_key = None
        duet_partial_repeats = 0
        # The guesser never sees the secret, only its own repeated utterances.
        duet_guesser_last_key = None
        duet_guesser_repeat_count = 0
        # Single accepted reaction per actual utterance even when the
        # explainer receives both peer ASR and the reliable LiveKit relay.
        duet_recent_guess_keys: dict[tuple, float] = {}
        compound_asr = CompoundTranscriptWindow()
        # Only the immediately preceding FINAL spoken HUMAN guess may be
        # approved by a short verbal "Да!" from an AI explainer.
        pending_human_guess = None
        score_lock = asyncio.Lock()
        role_selection_lock = asyncio.Lock()
        role_selection_done = False

        async def ensure_server_started_for_bot(reason: str) -> bool:
            """Commit an explicit HUMAN+AI role choice to the server once."""
            nonlocal role_selection_done
            if (mode != "game" or is_duo or scripted_partner or
                    role_selection_done or latest != "unselected"):
                return False
            async with role_selection_lock:
                if role_selection_done or stop.is_set():
                    return False
                try:
                    before = await game.context()
                    if (before.get("phase") not in ("turn_intro","turnintro")
                            or before.get("role") != "unselected"
                            or before.get("isPaused")):
                        return False
                    agent.suppress_next_word_commentary=True
                    await game.choose_explainer()
                    after = await game.context()
                    if after.get("phase") != "active" or after.get("role") != "explainer":
                        agent.suppress_next_word_commentary=False
                        return False
                    if not agent.prime_first_explainer_hint(after):
                        agent.suppress_next_word_commentary=False
                    role_selection_done = True
                    logger.info("AI role confirmed by server bot=%s reason=%s",
                                bot_uid[-6:],reason)
                    return True
                except Exception as error:
                    agent.suppress_next_word_commentary=False
                    logger.warning("AI role selection deferred bot=%s cause=%s",
                                   bot_uid[-6:],type(error).__name__)
                    return False

        last_immediate_hint = 0.0
        last_duet_reply_requested = 0.0
        last_duet_wrong_nudge = 0.0
        last_card_switched_at = 0.0
        last_logged_voice_key = None
        # A tool-committed approval may be finishing its delegated response.
        # Do not abort that response just because the game advanced.
        last_tool_score_at = 0.0
        active_prompt_key = None
        async def reset_orphaned_speech():
            # GPT-Live is server-driven: even force=False may break an
            # outstanding delegated tool result and leave the model SILENT.
            # No interruption is a valid recovery mechanism for this model.
            # The coordinator can emit native private guidance while the
            # existing model decides when to speak.
            return

        card_prompts = CardPromptCoordinator(
            generate=lambda text: session.generate_reply(instructions=text),
            commentary=lambda text: agent.duplex_session.append_commentary(text),
            reset=reset_orphaned_speech,
            require_voice=True,
            attempts=2,
            # A pending GPT-Live response isn't cancelled by the framework.
            # SDK refusal timeout is 10s: let it finish instead of spawning
            # overlapping requests after a 1.3s artificial timeout.
            response_timeout=10.5,
            # Native commentary goes to the already active audio model
            # immediately. Let it speak before advisory generate_reply.
            commentary_grace=1.0 if is_duo else 1.2,
            state=lambda: (str(getattr(session,"agent_state","")),
                           str(getattr(session,"user_state",""))),
            is_current=lambda key: (not stop.is_set()
                        and latest=="explainer" and key==active_prompt_key),
            spoke_at=lambda: last_agent_spoke,
            log=lambda event,attempt,reason: (
                logger.warning if event in (
                    "card_prompt_retry","card_prompt_exhausted") else logger.info
            )("GPT-Live %s bot=%s count=%d reason=%s",
                event,bot_uid[-6:],attempt,reason),
        )


        def request_duet_reply(instructions: str):
            """One explicit reply only when the duplex conversation is idle.

            Generating during partner speech or an existing response caused
            repeated 'model did not start speaking' refusals. Always retain
            the prompt as private instructions for the natural reply.
            Human+AI must keep its existing response path untouched.
            """
            nonlocal last_duet_reply_requested
            if not is_duo:
                return session.generate_reply(instructions=instructions)
            now = time.monotonic()
            agent_state = str(getattr(session, "agent_state", ""))
            peer_state = str(getattr(session, "user_state", ""))
            if (peer_state == "speaking"
                    or agent_state in ("speaking", "thinking", "initializing")
                    or now - last_duet_reply_requested < 1.25):
                agent.duplex_session.append_instructions(instructions)
                logger.debug("Duet reply deferred while speech is in flight: bot=%s",bot_uid[-6:])
                return None
            last_duet_reply_requested = now
            return session.generate_reply(instructions=instructions)

        async def stop_stale_explanation(*, verified_score: bool = False):
            # In this recording the model KEPT AUDIBLY EXPLAINING the old
            # answer after a verified MATCH. A full-duplex GPT-Live model
            # continues thinking internally after an interrupt, but LiveKit
            # can stop its LOCAL playout. Do this only on a real server
            # score/card change, never on VAD or an incorrect guess.
            if (not verified_score or
                    time.monotonic() - last_tool_score_at < 1.5 or
                    str(getattr(session,"agent_state","")) != "speaking"):
                return
            try:
                await asyncio.wait_for(session.interrupt(force=False),timeout=0.65)
                logger.info("GPT-Live stale_card_playout_stopped bot=%s",bot_uid[-6:])
            except (RuntimeError,asyncio.TimeoutError):
                logger.info("GPT-Live stale_card_playout_not_stopped bot=%s",bot_uid[-6:])

        def duet_agent_speech(event):
            nonlocal last_agent_spoke, last_logged_voice_key
            if watch_game_speech and str(getattr(event, "new_state", "")) == "speaking":
                last_agent_spoke = time.monotonic()
                duet_live.mark_spoke(last_agent_spoke)
                # Measure ACTUAL first audio after a card update, not when
                # the scheduler accepted a command. This exposes 10-20s
                # dead air without leaking secrets or user transcripts.
                if (active_prompt_key is not None and
                        active_prompt_key != last_logged_voice_key and
                        last_card_switched_at > 0):
                    logger.info(
                        "GPT-Live first_clue_audio bot=%s role=explainer "
                        "card_rev=%d latency_ms=%d",
                        bot_uid[-6:],active_prompt_key[3],
                        max(0,int((last_agent_spoke-last_card_switched_at)*1000)))
                    last_logged_voice_key=active_prompt_key

        def duet_partner_speech(event):
            nonlocal last_peer_speech_started_at
            if watch_game_speech and str(getattr(event, "new_state", "")) == "speaking":
                last_peer_speech_started_at = time.monotonic()
                duet_live.mark_heard(last_peer_speech_started_at)
                # LiveKit DuplexModel handles natural barge-in itself. NEVER
                # force-interrupt at VAD 'speaking' onset: this fires before
                # a complete word exists and repeatedly cut OFF BOTH bots.
                # Only a verified server card transition may forcibly stop
                # the old explanation.

        session.on("agent_state_changed", duet_agent_speech)
        session.on("user_state_changed", duet_partner_speech)

        async def on_heard(event):
            if not getattr(event, "is_final", False):
                return
            phrase = str(getattr(event, "transcript", "") or "").strip()
            if not phrase:
                return
            if watch_game_speech:
                duet_live.mark_heard(time.monotonic())
            # RoomOptions.participant_identity binds audio to the ONE human
            # teammate. Nobody from another team can trigger guess/skip.
            agent.last_human_phrase = phrase
            agent.human_input_version += 1
            input_version = agent.human_input_version
            if latest == "unselected":
                if _human_delegated_explaining(phrase):
                    await ensure_server_started_for_bot("human_delegated")
                return
            if latest != "explainer":
                return
            nonlocal duet_wrong_guesses, duet_guess_verification_pending
            nonlocal last_immediate_hint, last_duet_wrong_nudge, active_prompt_key
            nonlocal duet_partial_key, duet_partial_repeats
            nonlocal pending_human_guess
            candidate_card_key = active_prompt_key
            human_guess = None
            if solo_explainer and candidate_card_key:
                human_guess = {
                    "key": candidate_card_key, "phrase": phrase,
                    "at": time.monotonic(),
                    # The model may begin saying "Да" BEFORE the final ASR
                    # transcript arrives. The real VAD speech-onset time
                    # is the correct temporal boundary, not ASR end time.
                    "speech_started_at": (
                        last_peer_speech_started_at
                        if 0 < time.monotonic() - last_peer_speech_started_at < 8
                        else time.monotonic() - 2.0),
                    "status": "pending", "approval_requested": False,
                }
                pending_human_guess = human_guess
            if is_duo and candidate_card_key:
                dedup_key=(candidate_card_key,guess_signature(phrase))
                received_at=time.monotonic()
                if received_at-duet_recent_guess_keys.get(dedup_key,-100.0)<1.6:
                    return
                duet_recent_guess_keys[dedup_key]=received_at
                # Bound retained signatures to the current short live turn.
                if len(duet_recent_guess_keys)>64:
                    duet_recent_guess_keys.clear()
                    duet_recent_guess_keys[dedup_key]=received_at
            try:
                lower = phrase.lower()
                if any(x in lower for x in (
                    "пропусти", "пропускаем", "пропускай",
                    "следующее слово", "след слово", "давай след слово",
                    "давай следующее", "давай новое слово", "давай дальше",
                    "след карточку", "давай другое",
                )):
                    result = await agent.skip_once(phrase, input_version)
                    if result.get("skipped"):
                        if pending_human_guess is human_guess:
                            pending_human_guess=None
                        # Only a new authoritative card can issue its hint;
                        # no competing immediate generation from the old card.
                        await stop_stale_explanation()
                else:
                    if is_duo:
                        duet_guess_verification_pending = True
                    # Realtime ASR may finalize "снежный" and "человек"
                    # separately when the player INTERRUPTS our voice.
                    # Stitch only two recent partial fragments from the
                    # SAME authoritative secret card. The backend still
                    # verifies exact words before a score is awarded.
                    stitched = (
                        compound_asr.offer(
                            secret=candidate_card_key[2],
                            card_key=candidate_card_key,phrase=phrase,
                            now=time.monotonic())
                        if candidate_card_key and candidate_card_key[2]
                        else None
                    )
                    async with score_lock:
                        result = await game.heard(human_uid, phrase)
                        if (not result.get("matched") and stitched and
                                result.get("reason") == "FORM_NOT_EXACT"):
                            composed = await game.heard(human_uid,stitched)
                            if composed.get("matched"):
                                result=composed
                                logger.info(
                                    "Compound ASR fragments voice-scored bot=%s",
                                    bot_uid[-6:])
                        if human_guess is not None:
                            if result.get("matched"):
                                human_guess["status"]="matched"
                                if pending_human_guess is human_guess:
                                    pending_human_guess=None
                            else:
                                human_guess["status"]="unmatched"
                    if mode == "game":
                        # No phrases/secrets in production logs. See both human
                        # and AI+AI results when a player reports false "Верно".
                        logger.info("Peer voice score: mode=%s bot=%s result=%s",
                                    "duet" if is_duo else "solo",bot_uid[-6:],
                                    "MATCH" if result.get("matched")
                                    else str(result.get("reason") or "NO_MATCH")[:32])
                    if is_duo:
                        # No transcripts/secret words in logs: only the
                        # backend's rejection code for safe score diagnostics.
                        logger.info("Duet peer-voice guess: bot=%s result=%s",
                                    bot_uid[-6:], "MATCH" if result.get("matched")
                                    else str(result.get("reason") or "NO_MATCH")[:32])
                    if result.get("matched"):
                        # The point has already changed on the server. Never
                        # continue the old secret in the model's inner state.
                        agent.duplex_session.append_thinking(
                            "Уже угаданную загадку больше не объясняй. "
                            "Следующую подсказку строй исключительно по "
                            "новому слову, полученному из игрового контекста.")
                        compound_asr.clear()
                        agent.recent_word_keys.add(str(result.get("actionId") or ""))
                        duet_wrong_guesses = 0
                        # The server already advanced the card. Do not ask
                        # GPT-Live for another reply from the OLD-card ASR.
                        # The versioned card watcher owns the single NEW hint.
                        agent.suppress_next_word_commentary = False
                        if is_duo and not result.get("turnClosed"):
                            agent.pending_voice_confirmation = _voice_confirmation(
                                latest, True, len(agent.recent_word_keys))
                        # The watcher polls /context while the HTTP scoring
                        # request is in flight. It may ALREADY have seen the
                        # next card and scheduled its first spoken clue.
                        # Never cancel that NEW-card audio on completion of an
                        # OLD-card score: that race caused 40+ seconds silent
                        # after the first counted word in the Nov 16 recording.
                        if active_prompt_key == candidate_card_key:
                            active_prompt_key = None
                            await card_prompts.close()
                            # Interrupting SOLO GPT-Live at an exact MATCH
                            # stops only local audio, NOT its internal voice
                            # generation. The Nov 18 trace proves this caused
                            # a seven-second next-card stall. Let native VAD
                            # and the new-card commentary take over instead.
                            # DUET still uses explicit verified-score cutoff.
                            if is_duo:
                                await stop_stale_explanation(verified_score=True)
                            logger.info(
                                "Scored old card invalidated bot=%s",bot_uid[-6:])
                        else:
                            logger.info(
                                "Scored old card already advanced bot=%s next=%s",
                                bot_uid[-6:],bool(active_prompt_key))
                        if result.get("turnClosed"):
                            # Last-word grace: the score was committed, but
                            # there is NO next card. Finish voice immediately.
                            logger.info("Voice-scored last word closed turn bot=%s",
                                        bot_uid[-6:])
                            stop.set()
                    elif result.get("reason") == "FORM_NOT_EXACT" and is_duo:
                        duet_wrong_guesses += 1
                        if duet_wrong_guesses in (2, 4):
                            agent.duplex_session.append_thinking(
                                "Напарник уже пробовал назвать ответ. "
                                "Нужен другой образ или признак, без "
                                "повтора предыдущей подсказки.")
                        # A completed FINAL transcript from the linked agent
                        # is a real interruption, not a noisy VAD fragment.
                        # First verify it still belongs to the same card:
                        # the guesser's own voice may ALREADY have scored
                        # the word while this late ASR fragment is arriving.
                        now=time.monotonic()
                        partial = (partial_compound_guess(candidate_card_key[2], phrase)
                                   if candidate_card_key else None)
                        if (candidate_card_key and
                                candidate_card_key==active_prompt_key and
                                (now-last_card_switched_at>=0.9 or partial is not None) and
                                now-last_duet_wrong_nudge>=1.0):
                            current=await game.context()
                            still_same=(
                                current.get("role")=="explainer" and
                                current.get("phase")=="active" and
                                current.get("currentWord")==candidate_card_key[2] and
                                int(current.get("cardRevision") or 0)==candidate_card_key[3]
                            )
                            if still_same:
                                last_duet_wrong_nudge=now
                                if partial:
                                    signature = (candidate_card_key,
                                                 guess_signature(phrase))
                                    if signature == duet_partial_key:
                                        duet_partial_repeats += 1
                                    else:
                                        duet_partial_key = signature
                                        duet_partial_repeats = 1
                                    followup = (
                                        already_spoken_component_hint(
                                            candidate_card_key[2],partial) +
                                        partial.instruction(
                                            repeated=duet_partial_repeats > 1))
                                    agent.duplex_session.append_thinking(followup)
                                    # Repeated identical guesses must not cut off
                                    # every new explanation. Escalate at 2,4,6...
                                    if (duet_partial_repeats > 2 and
                                            duet_partial_repeats % 2 == 1):
                                        continue_followup = False
                                    else:
                                        continue_followup = True
                                    logger.info(
                                        "Duet partial composite heard bot=%s kind=%s repeat=%d",
                                        bot_uid[-6:],partial.kind,
                                        min(duet_partial_repeats,9))
                                else:
                                    duet_partial_key = None
                                    duet_partial_repeats = 0
                                    followup = (
                                        "Ответ напарника пока другой. Сразу дай "
                                        "иной короткий признак предмета, затем "
                                        "дослушай следующую попытку. "
                                        "Никаких комментариев о проверке.")
                                    continue_followup = True
                                # GPT-Live's native barge-in already yields to
                                # the real partner. Forcing session.interrupt()
                                # on every wrong guess aborts the pending reply
                                # and makes the very next generate_reply refuse.
                                # Only a CONFIRMED scored card transition may
                                # interrupt the old audio forcefully.
                                if continue_followup:
                                    card_prompts.schedule(candidate_card_key, followup)
                                    logger.info(
                                        "Duet final-guess followup queued bot=%s kind=%s",
                                        bot_uid[-6:],
                                        partial.kind if partial else "different")
                    elif result.get("reason") == "FORM_NOT_EXACT" and solo_explainer:
                        # The HUMAN's finalized guess also deserves a prompt
                        # response. Never react to raw microphone VAD.
                        if candidate_card_key and candidate_card_key==active_prompt_key:
                            current=await game.context()
                            still_same=(
                                current.get("role")=="explainer" and
                                current.get("phase")=="active" and
                                current.get("currentWord")==candidate_card_key[2] and
                                int(current.get("cardRevision") or 0)==candidate_card_key[3]
                            )
                            if still_same:
                                partial=partial_compound_guess(
                                    candidate_card_key[2],phrase)
                                followup=((already_spoken_component_hint(
                                              candidate_card_key[2],partial) +
                                          partial.instruction()) if partial else
                                    "Напарник только что предложил вариант. "
                                    "Если он ТОЧНО назвал ответ, СНАЧАЛА вызови "
                                    "confirm_correct_human_guess и только "
                                    "после успешного результата скажи одно "
                                    "«Верно!». Иначе НЕ подтверждай зачёт: "
                                    "дай один НОВЫЙ конкретный признак "
                                    "загаданного предмета без повтора.")
                                agent.duplex_session.append_thinking(followup)
                                # Preserve natural interruption when a human
                                # answers: forcing an extra interrupt leaves
                                # the realtime model silent after the guess.
                                card_prompts.schedule(candidate_card_key,followup)
                                logger.info(
                                    "Solo final-guess followup queued bot=%s partial=%s",
                                    bot_uid[-6:], bool(partial))
            except Exception as exc:
                logger.debug("No game action for transcribed utterance: %s", exc)
            finally:
                if is_duo:
                    duet_guess_verification_pending = False

        session.on("user_input_transcribed",
                   lambda event: asyncio.create_task(on_heard(event)))

        def on_duet_guess_packet(packet):
            # Own-voice relays go ONLY from the linked GUESSER to this
            # EXPLAINER. Browser packets, spectators, other teams, old
            # card revisions and arbitrary room data are all ignored.
            if not is_duo or latest != "explainer" or not active_prompt_key:
                return
            sender=str(getattr(getattr(packet,"participant",None),"identity","") or "")
            candidate=verified_duet_voice_packet(
                getattr(packet,"data",None),
                topic=str(getattr(packet,"topic","") or ""),
                sender=sender,expected_peer=human_uid,
                turn_id=str(active_prompt_key[0] or ""),
                revision=int(active_prompt_key[3] or 0))
            if candidate:
                logger.info("Duet verified own-voice relay received bot=%s",
                            bot_uid[-6:])
                asyncio.create_task(on_heard(
                    SimpleNamespace(is_final=True,transcript=candidate)))

        if is_duo:
            ctx.room.on("data_received",on_duet_guess_packet)

        async def on_guesser_own_voice(event):
            nonlocal duet_guesser_last_key, duet_guesser_repeat_count
            # GPT-Live publishes the assistant's OWN actual utterance as a
            # completed conversation item after the audio finishes. In a
            # two-AI round, this is a much more reliable independent ASR
            # source than the explainer's peer-input transcript alone.
            # No text is logged or stored; backend checks BOTH the
            # authenticated GUESSER seat and the current secret word.
            if mode != "game" or scripted_partner or not is_duo:
                # In human+AI, only the HUMAN explainer presses Угадали.
                # The AI merely speaks guesses and cannot auto-score.
                return
            item = getattr(event, "item", None)
            if (getattr(item, "role", "") != "assistant"
                    or getattr(item, "interrupted", False)
                    or latest != "guesser"
                    or last_agent_spoke <= 0
                    or time.monotonic() - last_agent_spoke > 15):
                return
            phrase = str(getattr(item, "text_content", "") or "").strip()
            if not phrase:
                return
            try:
                # Preserve the speech's card ID before the score request:
                # the server may advance while a response is in flight.
                own_turn=str(duet_snapshot.get("turnId") or "")
                own_revision=int(duet_snapshot.get("cardRevision") or 0)
                result = await game.duet_guesser_spoke(phrase)
                if (result.get("reason") == "FORM_NOT_EXACT"
                        and own_turn and len(phrase) <= 180):
                    # The GUESSER's own completed utterance is more reliable
                    # than the EXPLAINER's peer-input ASR under barge-in.
                    # Target only the authenticated teammate. Never send
                    # a secret word, full game context or private prompt.
                    try:
                        await ctx.room.local_participant.publish_data(
                            json.dumps({"v":1,"turnId":own_turn,
                                        "cardRevision":own_revision,
                                        "phrase":phrase},ensure_ascii=False),
                            reliable=True,destination_identities=[human_uid],
                            topic=DUET_VOICE_GUESS_TOPIC)
                        logger.info("Duet own-voice relay sent bot=%s",
                                    bot_uid[-6:])
                    except Exception as relay_error:
                        logger.debug("Duet voice relay unavailable: %s",
                                     type(relay_error).__name__)
                logger.info("Own-voice guess: mode=%s bot=%s result=%s",
                            "duet" if is_duo else "solo",
                            bot_uid[-6:], "MATCH" if result.get("matched")
                            else str(result.get("reason") or "NO_MATCH")[:32])
                if result.get("matched"):
                    duet_guesser_last_key = None
                    duet_guesser_repeat_count = 0
                    agent.recent_word_keys.add(str(result.get("actionId") or ""))
                    # Wait for the explainer to receive the server's new
                    # card. Do not confirm, invent next hints or announce points.
                    agent.duplex_session.append_instructions(
                        "СЕРВЕР ЗАСЧИТАЛ твою догадку. Ты УГАДЫВАЮЩИЙ: "
                        "только слушай следующую подсказку объясняющего. "
                        "Не подтверждай ответ и не называй очки."
                    )
                    logger.info("Duet self-voice guess scored: bot=%s",
                                bot_uid[-6:])
                elif result.get("reason") == "FORM_NOT_EXACT" and is_duo:
                    # No secret is available to this agent. Coach only from
                    # its OWN speech and current public card revision.
                    current_key = (duet_snapshot.get("turnId"),
                                   int(duet_snapshot.get("cardRevision") or 0))
                    signature = (current_key, guess_signature(phrase))
                    if signature == duet_guesser_last_key:
                        duet_guesser_repeat_count += 1
                    else:
                        duet_guesser_last_key = signature
                        duet_guesser_repeat_count = 1
                    if duet_guesser_repeat_count in (2, 4, 7):
                        agent.duplex_session.append_thinking(
                            "Ты уже произнёс эту же догадку. НЕ повторяй её "
                            "сама по себе! Дослушай новое уточнение напарника "
                            "и назови более полный или другой ответ. "
                            "Это может быть составное название: добавь "
                            "недостающее свойство или определение, если "
                            "подсказки к нему ведут. Тайное слово тебе "
                            "неизвестно. Не объявляй, что угадал.")
                        logger.info("Duet repeated own guess bot=%s repetitions=%d",
                                    bot_uid[-6:],duet_guesser_repeat_count)
            except Exception as exc:
                logger.debug("Duet self-voice guess was not scored: %s",
                             type(exc).__name__)

        async def on_assistant_role_commitment(event):
            # A model may SAY "I'll explain" without calling its optional
            # tool. Its own final speech also triggers the server transition.
            if mode != "game" or is_duo or scripted_partner or latest != "unselected":
                return
            item = getattr(event,"item",None)
            if (getattr(item,"role","") != "assistant"
                    or getattr(item,"interrupted",False)):
                return
            if _ai_committed_to_explaining(getattr(item,"text_content","")):
                await ensure_server_started_for_bot("bot_committed")

        async def commit_solo_approval(approval: str, source: str) -> bool:
            """Commit one verified spoken or delegated AI decision immediately.

            An assistant transcript is available only AFTER its audible
            response, so the delegated tool can score earlier while the model
            is responding. Both routes share the same single-use gate and
            server turn/card authorization. Never score from AI's secret.
            """
            nonlocal pending_human_guess, active_prompt_key, last_tool_score_at
            if not solo_explainer or latest != "explainer" or stop.is_set():
                return False
            guess = pending_human_guess
            if source == "tool":
                # The delegated GPT-Live backend can ask for a score while
                # the FINAL human ASR text or exact-match request is still
                # in flight. Wait briefly rather than rejecting a correct
                # answer and making the bot repeat "Верно" for seconds.
                deadline = time.monotonic() + 0.9
                while (time.monotonic() < deadline and
                       (guess is None or guess["status"] == "pending")):
                    await asyncio.sleep(0.075)
                    guess = pending_human_guess
            if not guess or guess.get("approval_requested"):
                return False
            candidate = guess["key"]
            if (candidate != active_prompt_key or
                    time.monotonic() - guess["at"] > 8.0 or
                    (source == "spoken" and
                     last_agent_spoke < guess["speech_started_at"] - 0.3)):
                return False
            async with score_lock:
                if (guess["status"] != "unmatched" or
                        candidate != active_prompt_key or
                        guess.get("approval_requested")):
                    return False
                guess["approval_requested"] = True
                result = await game.approve_human_guess(
                    human_uid, guess["phrase"], approval,
                    str(candidate[0]), int(candidate[3]))
            logger.info(
                "AI explainer approval: bot=%s source=%s scored=%s reason=%s",
                bot_uid[-6:], source, bool(result.get("matched")),
                str(result.get("reason") or "MATCH")[:24])
            if not result.get("matched"):
                return False
            if source == "tool":
                last_tool_score_at = time.monotonic()
            if pending_human_guess is guess:
                pending_human_guess = None
            compound_asr.clear()
            agent.recent_word_keys.add(str(result.get("actionId") or ""))
            agent.duplex_session.append_thinking(
                "Только что подтверждённое слово ЗАСЧИТАНО. "
                "Прежняя карточка закрыта навсегда. "
                "Не описывай её больше. Следующая придёт с сервера.")
            if active_prompt_key == candidate:
                active_prompt_key = None
                await card_prompts.close()
                # Interrupting a running delegated tool response can strand
                # GPT-Live for seconds. Natural tool speech continues; the
                # next authoritative card owns the following clue.
                # Even the fallback transcript arrived at the end of a
                # spoken turn. Never interrupt SOLO after an approval: it
                # can strand the pending GPT-Live backend delegation.
            if result.get("turnClosed"):
                stop.set()
            return True

        async def on_assistant_spoken_approval(event):
            """Backward-compatible speech fallback when model skips its tool."""
            if not solo_explainer or latest != "explainer" or stop.is_set():
                return
            item = getattr(event, "item", None)
            if (getattr(item, "role", "") != "assistant" or
                    getattr(item, "interrupted", False)):
                return
            approval = _standalone_voice_approval(
                getattr(item, "text_content", ""))
            if approval:
                try:
                    await commit_solo_approval(approval, "spoken")
                except Exception as exc:
                    logger.warning(
                        "AI verbal approval not committed bot=%s error=%s",
                        bot_uid[-6:], type(exc).__name__)

        # The delegated tool can settle a correct answer while GPT-Live is
        # still speaking. The ordinary completed-utterance fallback remains.
        if solo_explainer:
            agent.confirm_last_human_guess = commit_solo_approval

        async def on_solo_assistant_item(event):
            await on_assistant_role_commitment(event)
            await on_assistant_spoken_approval(event)

        if mode == "game" and not scripted_partner:
            if is_duo:
                session.on("conversation_item_added",
                           lambda event: asyncio.create_task(on_guesser_own_voice(event)))
            else:
                session.on("conversation_item_added",
                           lambda event: asyncio.create_task(on_solo_assistant_item(event)))

        async def follow_game_state():
            nonlocal latest, duet_wrong_guesses, active_prompt_key
            nonlocal last_card_switched_at, duet_partial_key, duet_partial_repeats
            nonlocal duet_guesser_last_key, duet_guesser_repeat_count
            nonlocal pending_human_guess
            previous = None
            pending_duet_ack = None
            while not stop.is_set():
                try:
                    state = await game.context()
                except Exception:
                    # A 409 immediately after a final counted word means the
                    # active turn has ended. Do not leave a stale hint audible
                    # while the paid LiveKit worker is shutting down.
                    active_prompt_key = None
                    await card_prompts.close()
                    await stop_stale_explanation(verified_score=True)
                    stop.set()
                    break
                role = state.get("role", "unselected")
                if watch_game_speech:
                    duet_snapshot.clear()
                    duet_snapshot.update(state)
                    duet_live.card_changed(
                        str(state.get("turnId") or ""), role,
                        state.get("currentWord") if role=="explainer" else None,
                        time.monotonic(),
                        revision=int(state.get("cardRevision") or 0),
                    )
                history = state.get("recentWords") or []
                if isinstance(history, list):
                    new_actions = [
                        row for row in reversed(history)
                        if isinstance(row, dict) and row.get("actionId")
                        and str(row["actionId"]) not in agent.recent_word_keys
                    ]
                    for row in new_actions:
                        agent.recent_word_keys.add(str(row["actionId"]))
                    if new_actions:
                        # The alternative self-voice route can score a word
                        # before the explainer's peer ASR emits a final text.
                        # Only the EXPLAINER acknowledges this verified score.
                        if is_duo and role == "explainer":
                            for scored in new_actions:
                                if (str(scored.get("actionId") or "").startswith("aig_")
                                        and scored.get("outcome") == "guessed"):
                                    pending_duet_ack = _voice_confirmation(
                                        role, True, len(agent.recent_word_keys))
                        # Keep memory in the running realtime model, not only
                        # in the next paid process after a round change.
                        agent.duplex_session.append_instructions(_history_prompt(history))
                        # Never speak from the recent-word history: it also
                        # reaches the GUESSER and can race the actual explainer.
                        # Spoken guess confirmations are issued once, only by
                        # on_heard after the server validates the voice guess.
                        # Manual "Угадали" clicks are intentionally silent.
                word = state.get("currentWord") if role == "explainer" else None
                key = (state.get("turnId"), role, word,
                       int(state.get("cardRevision") or 0), bool(state.get("isPaused")))
                latest = role
                active_prompt_key = key if role=="explainer" and word and (
                    state.get("phase") == "active" and not state.get("isPaused")) else None
                # The server may advance the card while the speech-match
                # HTTP request is still resolving. Let its explainer finish
                # selecting the acknowledgement before new-word commentary.
                if (pending_human_guess is not None and
                        pending_human_guess["key"] != key):
                    # A delayed assistant 'yes' from the PREVIOUS word must
                    # not grant the NEXT word a point.
                    pending_human_guess=None
                if key != previous and not (
                        is_duo and duet_guess_verification_pending):
                    # Switching cards is an actual gameplay transition, not
                    # another hint on the old one. The model's queued old
                    # audio must be interrupted before this new card.
                    changed_active_card=(previous is not None
                        and previous[1] == "explainer"
                        and (previous[2] != word or previous[3] != key[3]))
                    beginning_explanation=((previous is not None
                        and previous[1] == "unselected" and role == "explainer")
                        or (previous is None and role == "explainer" and not is_duo))
                    if changed_active_card or beginning_explanation:
                        last_card_switched_at=time.monotonic()
                    previous = key
                    if role == "explainer" and word:
                        if is_duo:
                            duet_wrong_guesses = 0
                            duet_partial_key = None
                            duet_partial_repeats = 0
                        # Only the explaining AI receives the word through
                        # authenticated server context. No word in room metadata.
                        if changed_active_card or beginning_explanation:
                            # Any unfinished greeting or old-card generation
                            # must yield to the server's first playable word.
                            if is_duo:
                                await stop_stale_explanation(
                                    verified_score=changed_active_card or beginning_explanation)
                        # A word is ephemeral private thinking, NOT a
                        # permanent instruction: older standing secrets were
                        # pulling Rublisha back to Museum after Penguin.
                        agent.duplex_session.append_thinking(
                            "Текущий игровой контекст. Ты объясняешь. "
                            "Загадано сейчас слово №" + str(key[3]) + ": "
                            + str(word) + ". Все прежние загадки закрыты. "
                            "Не произноси само слово или его корни. "
                            "Говори только игровую подсказку."
                        )
                        skip_second_prompt=agent.suppress_next_word_commentary
                        agent.suppress_next_word_commentary=False
                        if is_duo and agent.pending_voice_confirmation:
                            pending_duet_ack=agent.pending_voice_confirmation
                        if not skip_second_prompt and not (
                                (is_duo or scripted_partner)
                                and word == duet_bootstrap_word
                                and state.get("turnId") == duet_bootstrap_turn):
                            # append_commentary merely adds context; it does
                            # NOT reliably start a new spoken answer while
                            # the duplex model is working. A real score gets
                            # an explicit response with the NEW secret word.
                            # Only normal game speech: NEVER prompt the
                            # model with meta phrases like NEW CARD or SERVER.
                            reply_instruction=(
                                ("Скажи одно краткое «"
                                 + pending_duet_ack
                                 + "» и сразу дай другой признак "
                                   "загаданного предмета, не раскрывая его.")
                                if is_duo and pending_duet_ack else
                                "Немедленно произнеси короткую конкретную "
                                "подсказку к загаданному предмету (3–8 слов). "
                                "Без объявления действий, заголовков, "
                                "метакомментариев и вступлений. "
                                "Не называй ответ. Слушай догадки напарника."
                            )
                            card_prompts.schedule(
                                key, reply_instruction,
                                # A scored word needs guaranteed initiative:
                                # GPT-Live may spend 10s declining an advisory
                                # reply after old audio was interrupted. Arm a
                                # single native-commentary recovery if no fresh
                                # voice arrives, not a second generate_reply.
                                after_verified_score=changed_active_card,
                            )
                            pending_duet_ack = None
                            agent.pending_voice_confirmation = None
                    elif role == "smalltalk":
                        agent.duplex_session.append_instructions(
                            "РЕЖИМ SMOL TALK: разговаривай только с владельцем "
                            "неформально. Никаких игровых слов, очков, смены ролей."
                        )
                    elif role == "guesser":
                        duet_guesser_last_key = None
                        duet_guesser_repeat_count = 0
                        agent.duplex_session.append_instructions(
                            "ТВОЯ РОЛЬ: УГАДЫВАЮЩИЙ. Секретного слова не знаешь. "
                            + ("Дослушай подсказку. Отвечай только одним "
                               "конкретным вариантом без комментариев. "
                               "Если понял ответ во время подсказки, "
                               "НЕМЕДЛЕННО перебей объясняющего одним "
                               "словом. Не проси менять роли."
                               if is_duo else
                               "Слушай ТОЛЬКО своего напарника и называй "
                               "догадки голосом. Только ЧЕЛОВЕК-ОБЪЯСНЯЮЩИЙ "
                               "нажимает «Угадали» и подтверждает ответ. "
                               "Ты НЕ нажимаешь «Угадали» и НЕ пользуешься "
                               "инструментами зачёта вместо человека.")
                        )
                    elif role == "unselected":
                        agent.duplex_session.append_instructions(
                            ("Сервер ещё запускает ход. Твоя роль в ИИ-дуэте "
                             "назначена заранее; не обсуждай и не меняй её. "
                             "Дождись активного хода."
                             if is_duo else
                             "Сейчас выбор роли. Спроси, кто объясняет. "
                             "Если человек говорит, что объясняешь ты, "
                             "вызови choose_to_explain и только затем объясняй.")
                        )
                # A voice-scored AI answer should reach the explainer before
                # another multi-second clue starts. Poll more frequently only
                # for short live two-AI rounds (not for smalltalk).
                # Observe the next card within ~250ms after a confirmed
                # spoken score, not after a 700ms poll. This is still
                # low-rate compared with audio/event traffic.
                await asyncio.sleep(0.22 if role in ("explainer","guesser")
                                    else 0.7)

        async def duet_liveness_watchdog():
            nonlocal error_code
            # Only uses trusted turn state and LiveKit speech events.
            # Never sends private card text to guesser or logs transcripts.
            while not stop.is_set():
                await asyncio.sleep(0.75 if is_duo else 1.35)
                if not duet_snapshot:
                    continue
                # In ordinary human+AI games, the model only needs to be
                # pushed while EXPLAINING. Never inject guessed words or
                # filler into its legitimate role as human teammate's guesser.
                if solo_explainer and duet_snapshot.get("role") != "explainer":
                    continue
                now=time.monotonic()
                # A human pauses to think or offers another guess: do NOT
                # inject a competing scripted voice prompt every few seconds.
                # Recover only a genuinely stalled solo explainer.
                # The new-card queue handles immediate first hints. This
                # watchdog handles subsequent stalled clues on the same word.
                if card_prompts.busy:
                    continue
                try:
                    action=duet_live.decide(
                        now=now, epoch_ms=int(time.time()*1000),
                        phase=str(duet_snapshot.get("phase") or ""),
                        role=str(duet_snapshot.get("role") or ""),
                        word=duet_snapshot.get("currentWord"),
                        paused=duet_snapshot.get("isPaused") is True,
                        deadline_ms=int(duet_snapshot.get("deadlineMs") or 0),
                        agent_state=str(getattr(session, "agent_state", "")),
                        user_state=str(getattr(session, "user_state", "")),
                    )
                    if action=="failed":
                        logger.warning(
                            "%s explainer stalled after bounded voice prompts; bot=%s",
                            "Duet" if is_duo else "Solo", bot_uid[-6:])
                        error_code="DUET_DEADLOCK" if is_duo else "SOLO_EXPLAINER_STALLED"
                        stop.set()
                        return
                    if not action:
                        continue
                    if card_prompts.busy:
                        duet_live.pending=False
                        continue
                    # No session.interrupt() from watchdog: native GPT-Live
                    # barge-in already yields to real partner speech. Forced
                    # resets were precisely what stranded delegated tools.
                    recover_thinking=action.startswith("reset_")
                    # A new card may have just requested a response; do
                    # not race a second generate_reply with that request.
                    if is_duo and time.monotonic()-last_duet_reply_requested < 1.75:
                        duet_live.pending=False
                        continue
                    base_action=action.removeprefix("reset_")
                    phrase={
                        "give_hint": (
                            "Твоя очередь ОБЪЯСНЯТЬ. Не называй тайное слово. "
                            "Дай другой конкретный признак текущего предмета. "
                            "Не повторяй прежнюю подсказку и не говори о ходе игры."
                        ),
                        "make_guess": (
                            "Твоя очередь УГАДЫВАТЬ по уже услышанной подсказке. "
                            "Немедленно скажи одну конкретную догадку. "
                            "Не проси объясняющего передать тайное слово."
                        ),
                        "request_hint": (
                            "Ты угадываешь, но не слышишь подсказки. "
                            "Скажи очень коротко: Дай подсказку! "
                            "После ответа сразу пробуй угадать."
                        ),
                    }[base_action]
                    logger.info("Game speech recovery: mode=%s bot=%s role=%s prompt=%s "
                                "attempt=%s",
                                "duet" if is_duo else "solo",bot_uid[-6:],
                                duet_snapshot.get("role"),action,
                                duet_live.prompts_without_voice)
                    if base_action == "give_hint":
                        # All explainer speech, including recovery, goes
                        # through the SAME per-card coordinator. Previously
                        # watchdog generate_reply raced the new-card prompt,
                        # making the first ten seconds silent.
                        if active_prompt_key and not card_prompts.busy:
                            card_prompts.schedule(active_prompt_key,phrase)
                        duet_live.pending=False
                        continue
                    handle=request_duet_reply(phrase)
                    if handle is None:
                        duet_live.pending=False
                        continue
                    try:
                        await asyncio.wait_for(handle,timeout=9)
                        if handle.exception() is not None:
                            logger.warning("Duet recovery speech refused: bot=%s",
                                           bot_uid[-6:])
                    except asyncio.TimeoutError:
                        logger.warning("Duet recovery response timed out: bot=%s",
                                       bot_uid[-6:])
                        # Do not interrupt: this GPT-Live response continues
                        # running on the provider side even after the
                        # framework timeout; killing playout won't fix it.
                    finally:
                        duet_live.pending=False
                except Exception as exc:
                    duet_live.pending=False
                    logger.warning("Duet recovery monitor failure: %s",type(exc).__name__)

        error_code = ""
        ticker = None
        watcher = None
        try:
            await session.start(
                agent=agent,
                room=ctx.room,
                room_options=(room_io.RoomOptions(
                    # A scripted bot has no LiveKit microphone or participant.
                    # Keep voice OUTPUT enabled while disabling INPUT; do not
                    # accidentally bind a spectator or the room host as the
                    # imaginary bot's microphone.
                    audio_input=False,
                    audio_output=True,
                    text_input=False,
                    close_on_disconnect=False,
                    delete_room_on_close=False,
                ) if scripted_partner else room_io.RoomOptions(
                    # Only this team's AI teammate is a valid input.
                    participant_identity=human_uid,
                    participant_kinds=[rtc.ParticipantKind.PARTICIPANT_KIND_AGENT],
                    audio_input=True,
                    audio_output=True,
                    text_input=False,
                    close_on_disconnect=True,
                    delete_room_on_close=False,
                ) if is_duo else room_io.RoomOptions(
                    # Preserve the existing DEFAULT participant kind support
                    # for ordinary human teammates, including guests/SIP.
                    # The agent hears only its human teammate's identity,
                    # while its speech is an audible track for the WHOLE room.
                    participant_identity=human_uid,
                    audio_input=True,
                    audio_output=True,
                    text_input=False,
                    close_on_disconnect=True,
                    delete_room_on_close=False,
                )),
            )
            started = time.monotonic()

            async def report_usage():
                while not stop.is_set():
                    await asyncio.sleep(5)
                    voice, tokens_in, tokens_out = _model_usage(
                        session, time.monotonic() - started)
                    try:
                        await game.report(voice, tokens_in, tokens_out)
                    except Exception as exc:
                        logger.warning("GPT-Live usage callback failed: %s", exc)

            ticker = asyncio.create_task(report_usage())
            # Start the game-state watcher AFTER sending the initial voice
            # instruction. Otherwise the watcher and the opening generate_reply
            # race to make GPT-Live talk, and BOTH requests may be declined.
            if mode == "smalltalk":
                session.generate_reply(instructions=(
                    "Начни лёгкий разговор по-русски. "
                    + ("Поздоровайся коротко. " if should_greet else
                       "Не здоровайся — вы уже знакомы по этой партии. ")
                    + "Спроси у владельца что-нибудь ненавязчивое."
                ))
            elif scripted_partner:
                # In mixed test-only pairs, the scheduled scripted runner
                # supplies one SIMULATED word guess. The AI must explain,
                # since the ordinary test-bot doesn't publish audio at all.
                try:
                    await game.choose_explainer()
                    scripted_current=await game.context()
                    duet_bootstrap_word=scripted_current.get("currentWord")
                    duet_bootstrap_turn=scripted_current.get("turnId")
                    session.generate_reply(instructions=(
                        ("Представься кратко. " if should_greet else
                         "Не здоровайся повторно. ")
                        + "Ты объясняешь секретные слова в тестовой игре. "
                        + "Соседний тест-бот не разговаривает и делает "
                        + "только программные пробные догадки. Не выдавай его "
                        + "симуляцию за настоящую речь или понимание. "
                        + "Дай короткие голосовые подсказки к слову, которое "
                        + "тебе передаст сервер; не называй слово напрямую."
                    ))
                except Exception as exc:
                    logger.warning("Test scripted teammate turn not started: %s", exc)
            elif is_duo:
                logger.info("Duet audio linked: bot=%s peer=%s connected=%s",
                            bot_uid[-6:], human_uid[-6:],
                            getattr(getattr(session, "room_io", None), "linked_participant", None) is not None)
                if bot_uid == designated_explainer_uid:
                    # ONE agent initiates; second agent listens to REAL audio.
                    # Both calling generate_reply together caused mutual
                    # refusals from GPT-Live at room startup.
                    try:
                        await game.choose_explainer()
                        current=await game.context()
                        duet_bootstrap_word=current.get("currentWord")
                        duet_bootstrap_turn=current.get("turnId")
                        if not duet_bootstrap_word or current.get("role")!="explainer":
                            raise RuntimeError("duet secret word/role not ready")
                        # The FULL-DUPLEX model starts speech from native
                        # commentary. An immediate generate_reply competes
                        # with the still-in-flight role-start continuation
                        # and caused the 4-10 second opening silence.
                        if not agent.prime_first_explainer_hint(current):
                            raise RuntimeError("duet first card could not be primed")
                        logger.info("Duet first clue primed bot=%s",bot_uid[-6:])
                        # One watchdog owns any genuine later stall. No
                        # second competing startup response task is needed.
                    except Exception as exc:
                        logger.warning("Duo opening turn failed: %s", exc)
                        error_code="DUET_OPENING_FAILED"
                        stop.set()
                else:
                    # The guesser must LISTEN first: do not send another
                    # autonomous opening reply before the first voice stream.
                    agent.duplex_session.append_instructions(
                        "Ты угадываешь и не знаешь секретного слова. "
                        "Не говори первым: дождись первой голосовой подсказки "
                        "второго ИИ, затем отвечай одной догадкой за раз. "
                        "Не объявляй очки, пока сервер их не подтвердил."
                    )
            else:
                session.generate_reply(instructions=(
                    ("Поздоровайся с напарником в двух словах. " if should_greet else
                     "Не здоровайся повторно. ")
                    + "Коротко уточни, кто будет объяснять, а кто угадывать."
                ))
            watcher = asyncio.create_task(follow_game_state())
            if watch_game_speech:
                duet_watchdog_task=asyncio.create_task(duet_liveness_watchdog())
            try:
                await asyncio.wait_for(stop.wait(),
                                       timeout=min(180, max_seconds))
            except asyncio.TimeoutError:
                logger.info("GPT-Live reached max %ss", max_seconds)
        except Exception as exc:
            error_code = type(exc).__name__
            logger.warning("GPT-Live voice session failed: %s", exc)
        finally:
            stop.set()
            await card_prompts.close()
            for task in (ticker, watcher, duet_first_reply_task, duet_watchdog_task):
                if task is not None:
                    task.cancel()
                    try:
                        await task
                    except asyncio.CancelledError:
                        pass
            try:
                await session.aclose()
            except Exception:
                logger.debug("GPT-Live aclose failed", exc_info=True)
            seconds = max(0.0, time.monotonic() - started) if started else 0.0
            voice, tokens_in, tokens_out = _model_usage(session, seconds)
            try:
                await game.report(voice, tokens_in, tokens_out,
                                  finished=True, error_code=error_code)
            except Exception:
                logger.exception("Final GPT-Live usage report could not be saved")


if __name__ == "__main__":
    cli.run_app(server)
