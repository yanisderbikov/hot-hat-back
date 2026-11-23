#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
mkdir -p models
sudo docker run --rm --gpus all \
  --user "$(id -u):$(id -g)" \
  -e HOT_HAT_MODELS_DIR=/models \
  -e HF_HOME=/models/.huggingface \
  -v "$PWD/models:/models" \
  --entrypoint python \
  hot-hat/gpu-worker:scrum-65 -m app.download_models
echo 'Models downloaded. GPU model inference still needs real-clip verification.'
