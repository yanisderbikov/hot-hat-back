#!/usr/bin/env bash
# Launch an outbound-only Immers RTX 4090 worker; keep HTTP/8088 localhost.
set -euo pipefail
cd "$(dirname "$0")"
if [ ! -f .env ] || ! grep -q '^HOT_HAT_GPU_TOKEN=' .env; then
  echo 'No private HOT_HAT_GPU_TOKEN. Run bash deploy.sh first.' >&2
  exit 2
fi
mkdir -p models clips
for model in longpipe-xl-matting.onnx longpipe-xl-flow.onnx birefnet-lite-fp16.onnx; do
  if [ ! -s "models/$model" ]; then
    echo "Model $model missing. Run bash download_models.sh." >&2
    exit 2
  fi
done
sudo docker rm -f hot-hat-gpu-queue >/dev/null 2>&1 || true
sudo docker run -d --name hot-hat-gpu-queue --restart unless-stopped \
  --gpus all --env-file "$PWD/.env" \
  --health-cmd "python -m app.queue_healthcheck" \
  --health-interval 20s --health-timeout 5s --health-start-period 60s \
  --health-retries 3 \
  -v "$PWD/models:/models:ro" -v "$PWD/clips:/clips" \
  --entrypoint python hot-hat/gpu-worker:scrum-65 -m app.queue_worker
echo 'Outbound GPU queue container started; no public port exposed.'
echo 'Railway needs only HOT_HAT_GPU_TOKEN_SHA256 (fingerprint below), not the token.'
python3 - <<'PY'
import hashlib
from pathlib import Path
text = Path(".env").read_text()
tokens = [x[18:] for x in text.splitlines() if x.startswith("HOT_HAT_GPU_TOKEN=")]
if len(tokens) != 1 or len(tokens[0]) < 32:
    raise SystemExit("Invalid private token in .env")
print("HOT_HAT_GPU_TOKEN_SHA256=" + hashlib.sha256(tokens[0].encode()).hexdigest())
PY
echo 'Checking actual authenticated Railway polling, not the unrelated HTTP /health route...'
ready=0
for attempt in $(seq 1 15); do
  status="$(sudo docker inspect --format '{{.State.Health.Status}}' hot-hat-gpu-queue 2>/dev/null || echo missing)"
  if [ "$status" = healthy ]; then
    ready=1
    break
  fi
  if [ "$status" = missing ] || [ "$(sudo docker inspect --format '{{.State.Running}}' hot-hat-gpu-queue 2>/dev/null)" != true ]; then
    break
  fi
  sleep 4
done
echo 'Container status:'
sudo docker ps -a --filter name=hot-hat-gpu-queue
echo 'Queue diagnostics (no secrets):'
sudo docker logs --tail 40 hot-hat-gpu-queue 2>&1 || true
if [ "$ready" != 1 ]; then
  echo 'GPU queue has not confirmed Railway HTTP 200 yet; inspect status above.' >&2
  exit 1
fi
echo 'GPU queue is HEALTHY: authorized Railway queue poll confirmed; no paid request made.'
