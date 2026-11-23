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
  -v "$PWD/models:/models:ro" -v "$PWD/clips:/clips" \
  --entrypoint python hot-hat/gpu-worker:scrum-65 -m app.queue_worker
echo 'Outbound GPU queue container started; no public port exposed.'
echo 'Railway HOT_HAT_GPU_TOKEN must equal the private token in gpu-worker/.env.'
echo 'Diagnostics: sudo docker logs --tail 30 hot-hat-gpu-queue'
