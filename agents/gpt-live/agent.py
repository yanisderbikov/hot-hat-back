"""Standalone HOT-HAT voice agent worker. NOT started by the Java service.

A named explicit LiveKit AgentDispatch is required; no ordinary room dispatches
this agent automatically. The feature is OFF unless explicitly enabled.
"""
import asyncio
import logging

from livekit.agents import Agent, AgentServer, AgentSession, JobContext, cli, inference

from config import (
    AGENT_NAME,
    BACKEND_INSTRUCTIONS,
    BACKEND_MODEL,
    MODEL_ID,
    VOICE_INSTRUCTIONS,
    read_settings,
)

logger = logging.getLogger("hot-hat-gpt-live")
server = AgentServer()


@server.rtc_session(agent_name=AGENT_NAME)
async def hot_hat_voice_player(ctx: JobContext) -> None:
    settings = read_settings()
    if not settings.enabled:
        logger.warning("GPT-Live dispatch ignored: HOT_HAT_GPT_LIVE_ENABLED=false")
        return

    # LiveKit Inference bills the connected LiveKit Cloud account.
    # Its existing LIVEKIT_URL/API_KEY/API_SECRET environment variables are
    # used automatically. A separate OPENAI_API_KEY is not needed.
    session = AgentSession(
        llm=inference.DuplexModel(
            model=MODEL_ID,
            voice=settings.voice,
            extra_kwargs={
                "responses_options": {
                    "model": BACKEND_MODEL,
                    "instructions": BACKEND_INSTRUCTIONS,
                }
            },
        )
    )
    closed = asyncio.Event()
    session.on("close", lambda *_: closed.set())

    try:
        await session.start(
            agent=Agent(instructions=VOICE_INSTRUCTIONS),
            room=ctx.room,
        )
        await ctx.connect()
        # This is only the VOICE transport MVP. The Java backend still owns all
        # secret words, guesses and scoring; game-tool access is NOT yet wired.
        # No automatic game action or hidden-word disclosure is possible here.
        await asyncio.wait_for(closed.wait(), timeout=settings.max_session_seconds)
    except asyncio.TimeoutError:
        logger.info("GPT-Live test session reached %s-second cap", settings.max_session_seconds)
    finally:
        await session.aclose()


if __name__ == "__main__":
    cli.run_app(server)
