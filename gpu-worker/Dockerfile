FROM pytorch/pytorch:2.5.1-cuda12.4-cudnn9-runtime

ENV PYTHONDONTWRITEBYTECODE=1 PYTHONUNBUFFERED=1
RUN apt-get update && apt-get install -y --no-install-recommends ffmpeg \
    && rm -rf /var/lib/apt/lists/*
WORKDIR /srv/hot-hat
COPY requirements.txt ./
RUN python -m pip install --no-cache-dir -r requirements.txt
COPY app ./app
RUN useradd --system --uid 10001 --create-home appuser && chown -R appuser:appuser /srv/hot-hat
USER appuser
EXPOSE 8088
HEALTHCHECK --interval=30s --timeout=5s --start-period=30s --retries=3 \
  CMD python -c "import urllib.request; urllib.request.urlopen('http://127.0.0.1:8088/health', timeout=3)"
CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8088", "--no-access-log"]
