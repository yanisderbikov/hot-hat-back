FROM python:3.12-slim
WORKDIR /app
ENV PYTHONDONTWRITEBYTECODE=1 PYTHONUNBUFFERED=1
COPY agents/gpt-live/requirements.txt /app/requirements.txt
RUN pip install --no-cache-dir -r requirements.txt
COPY agents/gpt-live/ /app/
# Download Silero ONNX weights while building, not during the first paid
# game turn. Jobs prewarm VAD locally before they join the room.
RUN python agent.py download-files
# Fail the build if the local ONNX detector cannot load. A game must never
# discover missing model assets only after its paid 60-second turn begins.
RUN python -c "from livekit.plugins import silero; silero.VAD.load(min_speech_duration=0.12, min_silence_duration=0.35, activation_threshold=0.55); print('Silero VAD preload smoke PASS')"
RUN python -m compileall -q /app && python -m unittest -q test_config test_duet_liveness test_card_prompts \
    && python -c "import agent; import game_client; print('HOT-HAT GPT-Live import smoke PASS')"
# Worker is always registered by name but NEVER receives game jobs unless
# the backend dispatch flag is explicitly enabled and a room team is active.
CMD ["python", "agent.py", "start"]
