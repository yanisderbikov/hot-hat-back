#!/usr/bin/env bash
# Test real ONNX CUDA inference for all model heads, not a fake CPU-only smoke.
set -euo pipefail
cd "$(dirname "$0")"
if [ ! -d models ] || [ ! -f models/birefnet-lite-fp16.onnx ]; then
  echo 'Models are not downloaded; run bash download_models.sh first.' >&2
  exit 2
fi
sudo docker run --rm --gpus all \
  --user "$(id -u):$(id -g)" \
  --network none \
  -v "$PWD/models:/models:ro" \
  --entrypoint python \
  hot-hat/gpu-worker:scrum-65 -m app.verify_models
