"""HOT-HAT: explicit per-active-team GPT-Live 1 with server-authoritative game tools.

This worker NEVER joins a room automatically and never starts an inference
session unless HOT_HAT_GPT_LIVE_ENABLED=true AND a valid server-dispatched
session was created for the currently active unranked AI+human pair.
"""
from __future__ import annotations

import asyncio
import inspect
import json
import logging
import os
import time

from livekit.agents import (
    Agent, AgentServer, AgentSession, JobContext, RoomOptions,
    RunContext, cli, inference,
)
from livekit.agents.llm import function_tool

from config import (
    AGENT_NAME, BACKEND_INSTRUCTIONS, BACKEND_MODEL, MODEL_ID,
    VOICE_INSTRUCTIONS, read_settings,
)
from game_client import GameClient

logger = logging.getLogger("hot-hat-gpt-live")
server = AgentServer()


class HotHatPlayer(Agent):
    def __init__(self, game: GameClient, human_uid: str):
        self.game = game
        self.human_uid = human_uid
        self.last_human_phrase = ""
        super().__init__(
            instructions=VOICE_INSTRUCTIONS + (
                " Сейчас ты играешь ровно с одним человеком, его голос "
                "единственный на твоём входе. В начале хода обсудите, кто "
                "объясняет. Когда партнёр говорит 'объясняй ты', НЕМЕДЛЕННО "
                "вызови choose_to_explain. Если партнёр сам начал объяснять, "
                "слушай, предлагай догадки голосом. Когда объясняешь ты, "
                "НИ ПРИ КАКИХ ОБСТОЯТЕЛЬСТВАХ не произноси слово полностью, "
                "его части или однокоренные формы. Чётко давай подсказки. "
                "При 'пропусти' вызови skip_after_teammate_request. "
                "Никогда не начисляй очки голосом и не меняй таймер сам."
            )
        )

    @function_tool
    async def choose_to_explain(self, context: RunContext) -> str:
        """Выбрать роль объясняющего и запустить минутный раунд, только
        когда напарник явно просит тебя объяснять слово."""
        try:
            state = await self.game.choose_explainer()
            return "Роль выбрана сервером. Начинай объяснять, получив слово: " + str(state.get("outcome"))
        except Exception:
            return "Не получилось запустить ход. Возможно, напарник уже начал объяснять."

    @function_tool
    async def skip_after_teammate_request(self, context: RunContext) -> str:
        """Пропустить текущую карточку, ТОЛЬКО если настоящий напарник
        явно сказал 'пропусти', 'пропускаем' или 'давай другое'."""
        if not self.last_human_phrase:
            return "Сначала нужно услышать просьбу напарника."
        try:
            await self.game.skip(self.human_uid, self.last_human_phrase)
            return "Сервер пропустил слово, переключаюсь на следующее."
        except Exception:
            return "Сервер не подтвердил просьбу пропустить слово."

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


@server.rtc_session(agent_name=AGENT_NAME)
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
        human_uid = str(initial.get("humanUid") or "")
        if not human_uid:
            return

        await ctx.connect()
        try:
            set_metadata = getattr(ctx.room.local_participant, "set_metadata", None)
            if set_metadata is not None:
                value = set_metadata(json.dumps({
                    "hotHatAiUid": bot_uid, "kind": "hot-hat-gpt-live",
                }))
                if inspect.isawaitable(value):
                    await value
        except Exception:
            logger.info("Agent participant metadata unavailable, audio still works")

        agent = HotHatPlayer(game, human_uid)
        session = AgentSession(llm=inference.DuplexModel(
            model=MODEL_ID,
            voice=settings.voice,
            extra_kwargs={"responses_options": {
                "model": BACKEND_MODEL,
                "instructions": BACKEND_INSTRUCTIONS,
            }},
        ))
        stop = asyncio.Event()
        session.on("close", lambda *_: stop.set())
        latest = ""
        started = 0.0

        async def on_heard(event):
            if not getattr(event, "is_final", False):
                return
            phrase = str(getattr(event, "transcript", "") or "").strip()
            if not phrase:
                return
            # RoomOptions.participant_identity binds audio to the ONE human
            # teammate. Nobody from another team can trigger guess/skip.
            agent.last_human_phrase = phrase
            if latest != "explainer":
                return
            try:
                lower = phrase.lower()
                if any(x in lower for x in (
                    "пропусти", "пропускаем", "пропускай",
                    "следующее слово", "давай другое",
                )):
                    await game.skip(human_uid, phrase)
                else:
                    await game.heard(human_uid, phrase)
            except Exception as exc:
                logger.debug("No game action for transcribed utterance: %s", exc)

        session.on("user_input_transcribed",
                   lambda event: asyncio.create_task(on_heard(event)))

        async def follow_game_state():
            nonlocal latest
            previous = None
            while not stop.is_set():
                try:
                    state = await game.context()
                except Exception:
                    stop.set()
                    break
                role = state.get("role", "unselected")
                word = state.get("currentWord") if role == "explainer" else None
                key = (state.get("turnId"), role, word, bool(state.get("isPaused")))
                latest = role
                if key != previous:
                    previous = key
                    if role == "explainer" and word:
                        # Only the explaining AI receives the word through
                        # authenticated server context. No word in room metadata.
                        agent.duplex_session.append_instructions(
                            "ТВОЯ РОЛЬ: ОБЪЯСНЯЮЩИЙ. Текущее секретное слово: "
                            + str(word) +
                            ". Объясняй по-русски, НЕ произнося слово ни в какой "
                            "форме. При точном произнесении слова человеком "
                            "сервер сам засчитает карточку."
                        )
                        agent.duplex_session.append_commentary(
                            "Начни объяснять новое слово короткими подсказками."
                        )
                    elif role == "guesser":
                        agent.duplex_session.append_instructions(
                            "ТВОЯ РОЛЬ: УГАДЫВАЮЩИЙ. Секретного слова не знаешь. "
                            "Слушай напарника и произноси догадки, но не говори "
                            "что сервер засчитал очко без подтверждения."
                        )
                    elif role == "unselected":
                        agent.duplex_session.append_instructions(
                            "Сейчас выбор роли. Спроси, кто объясняет. "
                            "Если человек говорит, что объясняешь ты, "
                            "вызови choose_to_explain и только затем объясняй."
                        )
                await asyncio.sleep(0.7)

        error_code = ""
        ticker = None
        watcher = None
        try:
            await session.start(
                agent=agent,
                room=ctx.room,
                room_options=RoomOptions(
                    participant_identity=human_uid,
                    close_on_disconnect=True,
                    delete_room_on_close=False,
                ),
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
            watcher = asyncio.create_task(follow_game_state())
            session.generate_reply(instructions=(
                "Поприветствуй напарника в двух словах и спроси, "
                "кто сейчас будет объяснять, а кто угадывать."
            ))
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
            for task in (ticker, watcher):
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
