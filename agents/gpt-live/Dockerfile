FROM python:3.12-slim
WORKDIR /app
ENV PYTHONDONTWRITEBYTECODE=1 PYTHONUNBUFFERED=1
COPY agents/gpt-live/requirements.txt /app/requirements.txt
RUN pip install --no-cache-dir -r requirements.txt
COPY agents/gpt-live/ /app/
RUN python -m compileall -q /app && python -m unittest -q test_config
# Worker is always registered by name but NEVER receives game jobs unless
# the backend dispatch flag is explicitly enabled and a room team is active.
CMD ["python", "agent.py", "start"]
