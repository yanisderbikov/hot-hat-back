#!/usr/bin/env bash
# Deploys a localhost-only development GPU worker on the rented Immers host.
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

echo 'Worker runs on the GPU host, bound to 127.0.0.1:8088 only.'
curl --retry 12 --retry-delay 2 --retry-connrefused -fsS http://127.0.0.1:8088/health
echo
