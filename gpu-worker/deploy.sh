#!/usr/bin/env bash
# SCRUM-65: idempotent local-only dev deployment on the Immers RTX 4090.
set -euo pipefail
cd "$(dirname "$0")"

command -v openssl >/dev/null || { echo 'openssl is required' >&2; exit 1; }
command -v curl >/dev/null || { echo 'curl is required' >&2; exit 1; }

if [ ! -f .env ]; then
  umask 077
  printf 'HOT_HAT_GPU_TOKEN=%s\n' "$(openssl rand -hex 32)" > .env
  echo 'Generated private token in gpu-worker/.env (do not commit or share).'
fi

sudo docker build -t hot-hat/gpu-worker:scrum-65 .
sudo docker rm -f hot-hat-gpu-worker >/dev/null 2>&1 || true
sudo docker run -d \
  --name hot-hat-gpu-worker \
  --restart unless-stopped \
  --gpus all \
  --env-file .env \
  --publish 127.0.0.1:8088:8088 \
  --security-opt no-new-privileges:true \
  hot-hat/gpu-worker:scrum-65

echo 'Worker started; waiting for Python/PyTorch initialization on localhost...'
ready=false
for attempt in $(seq 1 45); do
  # PyTorch imports can take seconds, and curl may see both refused and reset
  # connections during startup. Treat both as transient until the deadline.
  if curl --silent --show-error --fail --max-time 3 \
      http://127.0.0.1:8088/health 2>/dev/null; then
    echo
    ready=true
    break
  fi
  state="$(sudo docker inspect --format '{{.State.Status}}' hot-hat-gpu-worker 2>/dev/null || true)"
  if [ "$state" != "running" ]; then
    echo "Container state: $state" >&2
    break
  fi
  sleep 2
done

if [ "$ready" != true ]; then
  echo 'GPU worker health check failed. Container diagnostics:' >&2
  sudo docker ps -a --filter name=hot-hat-gpu-worker >&2
  sudo docker logs --tail 80 hot-hat-gpu-worker >&2 || true
  exit 1
fi
echo 'GPU worker health is OK. Port 8088 is bound to 127.0.0.1 only.'
