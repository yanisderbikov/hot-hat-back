#!/usr/bin/env bash
# SCRUM-65: repeat a PUBLIC one-second clip to exercise a 3-second,
# 30-frame real ONNX CUDA + alpha/audio export. This is NOT a motion-quality benchmark.
set -euo pipefail
cd "$(dirname "$0")"
mkdir -p clips
SOURCE_URL='https://raw.githubusercontent.com/gordicaleksa/pytorch-naive-video-neural-style-transfer/3773483926f9f96534c45dc35251e296185a69e8/data/example.mp4'

command -v curl >/dev/null || { echo 'curl is required' >&2; exit 2; }
rm -f clips/scrum65-public-person-source.mp4 \
      clips/scrum65-public-person.mp4 \
      clips/scrum65-public-person-muted.mp4 \
      clips/scrum65-public-person-alpha.webm \
      clips/scrum65-public-person-preview.png
echo 'Downloading a small public example with a person...'
curl --fail --location --retry 3 --max-time 60 --max-filesize 10000000 \
    --output clips/scrum65-public-person-source.mp4 "$SOURCE_URL"

echo 'Preparing 3 seconds / 30 frames at 640x360 (video only)...'
# The pinned source contains ~1s footage. Repeat only for smoke testing.
sudo docker run --rm --network none \
  --user "$(id -u):$(id -g)" \
  -v "$PWD/clips:/clips" --entrypoint ffmpeg \
  hot-hat/gpu-worker:scrum-65 \
  -nostdin -hide_banner -loglevel error -y \
  -stream_loop -1 -i /clips/scrum65-public-person-source.mp4 \
  -map 0:v:0 -t 3 -frames:v 30 \
  -vf scale=640:360,fps=10 -an -c:v libx264 -preset ultrafast \
  -pix_fmt yuv420p /clips/scrum65-public-person-muted.mp4

echo 'Adding a full 3-second test audio track in a separate mux pass...'
sudo docker run --rm --network none \
  --user "$(id -u):$(id -g)" \
  -v "$PWD/clips:/clips" --entrypoint ffmpeg \
  hot-hat/gpu-worker:scrum-65 \
  -nostdin -hide_banner -loglevel error -y \
  -i /clips/scrum65-public-person-muted.mp4 \
  -f lavfi -i sine=frequency=440:sample_rate=48000:duration=3 \
  -map 0:v:0 -map 1:a:0 -t 3 -c:v copy \
  -c:a aac -b:a 96k /clips/scrum65-public-person.mp4

get_frames() {
  sudo docker run --rm --network none \
    --user "$(id -u):$(id -g)" \
    -v "$PWD/clips:/clips:ro" --entrypoint ffprobe \
    hot-hat/gpu-worker:scrum-65 \
    -v error -select_streams v:0 -count_frames \
    -show_entries stream=nb_read_frames -of default=nw=1:nk=1 "/clips/$1" | tr -d '\r\n'
}
input_frames="$(get_frames scrum65-public-person.mp4)"
if [ "$input_frames" != "30" ]; then
  echo "Test input must have 30 video frames, got '$input_frames'." >&2
  exit 1
fi

echo 'Processing the 30-frame test MP4 on OUR RTX 4090...'
time bash process_mp4.sh \
  clips/scrum65-public-person.mp4 \
  clips/scrum65-public-person-alpha.webm

output_frames="$(get_frames scrum65-public-person-alpha.webm)"
if [ "$output_frames" != "30" ]; then
  echo "Test output must have 30 video frames, got '$output_frames'." >&2
  exit 1
fi
echo 'All 30 video frames were processed and exported.'

echo 'Verifying decoded alpha (not just metadata) and preserved audio...'
sudo docker run --rm --network none \
  --user "$(id -u):$(id -g)" \
  -v "$PWD/clips:/clips" --entrypoint python \
  hot-hat/gpu-worker:scrum-65 -m app.inspect_alpha \
  --input /clips/scrum65-public-person-alpha.webm \
  --preview /clips/scrum65-public-person-preview.png
echo 'Test complete. Visually inspect clips/scrum65-public-person-preview.png.'
