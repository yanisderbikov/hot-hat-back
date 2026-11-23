#!/usr/bin/env bash
# Public human-in-video proof of a real RTX 4090 MP4 -> VP9 alpha WebM pipeline.
# Avoids the user's paid/private neuro-replacement assets.
set -euo pipefail
cd "$(dirname "$0")"
mkdir -p clips
SOURCE_URL='https://raw.githubusercontent.com/gordicaleksa/pytorch-naive-video-neural-style-transfer/3773483926f9f96534c45dc35251e296185a69e8/data/example.mp4'

command -v curl >/dev/null || { echo 'curl is required' >&2; exit 2; }
rm -f clips/scrum65-public-person-source.mp4 \
      clips/scrum65-public-person.mp4 \
      clips/scrum65-public-person-alpha.webm \
      clips/scrum65-public-person-preview.png
echo 'Downloading a small public example with a person...'
curl --fail --location --retry 3 --max-time 60 --max-filesize 10000000 \
    --output clips/scrum65-public-person-source.mp4 "$SOURCE_URL"

echo 'Preparing a short 640x360 MP4 with an audio track, inside Docker...'
sudo docker run --rm --network none \
  --user "$(id -u):$(id -g)" \
  -v "$PWD/clips:/clips" --entrypoint ffmpeg \
  hot-hat/gpu-worker:scrum-65 \
  -nostdin -hide_banner -loglevel error -y \
  -i /clips/scrum65-public-person-source.mp4 \
  -f lavfi -i sine=frequency=440:sample_rate=48000 \
  -map 0:v:0 -map 1:a:0 -t 3 \
  -vf scale=640:360,fps=10 -c:v libx264 -preset ultrafast \
  -pix_fmt yuv420p -c:a aac -b:a 96k -shortest \
  /clips/scrum65-public-person.mp4

echo 'Processing real person video on our RTX 4090...'
time bash process_mp4.sh \
  clips/scrum65-public-person.mp4 \
  clips/scrum65-public-person-alpha.webm

echo 'Verifying decoded alpha (not just metadata) and preserved audio...'
sudo docker run --rm --network none \
  --user "$(id -u):$(id -g)" \
  -v "$PWD/clips:/clips" --entrypoint python \
  hot-hat/gpu-worker:scrum-65 -m app.inspect_alpha \
  --input /clips/scrum65-public-person-alpha.webm \
  --preview /clips/scrum65-public-person-preview.png
echo 'Test complete. Inspect clips/scrum65-public-person-preview.png visually.'
