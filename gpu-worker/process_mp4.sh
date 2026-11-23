#!/usr/bin/env bash
# Usage: bash process_mp4.sh clips/original.mp4 clips/transparent.webm
set -euo pipefail
cd "$(dirname "$0")"
if [ "$#" -ne 2 ]; then
  echo "Usage: bash process_mp4.sh clips/input.mp4 clips/output.webm" >&2
  exit 2
fi
mkdir -p clips models
source="$(realpath -m "$1")"; dest="$(realpath -m "$2")"
case "$source" in "$PWD"/clips/*) ;; *) echo "Input must be inside $PWD/clips" >&2; exit 2 ;; esac
case "$dest" in "$PWD"/clips/*) ;; *) echo "Output must be inside $PWD/clips" >&2; exit 2 ;; esac
if [ ! -f "$source" ]; then echo "Source MP4 not found: $source" >&2; exit 2; fi
exec flock -n /tmp/hot-hat-gpu-offline.lock sudo docker run --rm --gpus all \
  --user "$(id -u):$(id -g)" \
  --network none \
  -v "$PWD/models:/models:ro" \
  -v "$PWD/clips:/clips" \
  --entrypoint python \
  hot-hat/gpu-worker:scrum-65 -m app.matting \
  --input "/clips/$(basename "$source")" --output "/clips/$(basename "$dest")"
