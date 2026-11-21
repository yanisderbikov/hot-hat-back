FROM node:22-bookworm-slim
RUN apt-get update && apt-get install -y --no-install-recommends python3 python3-venv ffmpeg ca-certificates \
    && python3 -m venv /opt/yt-dlp \
    && /opt/yt-dlp/bin/pip install --no-cache-dir "yt-dlp[default]==2026.10.26" \
    && rm -rf /var/lib/apt/lists/*
RUN node -e 'if(Number(process.versions.node.split(".")[0]) < 22) process.exit(1)' \
    && /opt/yt-dlp/bin/python -c "import importlib.metadata as m; print('yt-dlp-ejs',m.version('yt-dlp-ejs'))"
COPY remote-extractor/clip_server.py /app/clip_server.py
COPY remote-extractor/test_age_auth.py /app/test_age_auth.py
RUN cd /app && python3 -m unittest -q test_age_auth
WORKDIR /app
ENV PYTHONUNBUFFERED=1
ENTRYPOINT ["python3","/app/clip_server.py"]
