# HOT-HAT private clip source
Runs as a separate Railway service outside the main game's region.
It only accepts POST /clip with X-Hot-Hat-Clip-Token, validates a YouTube video ID and startSeconds, and returns a temporary 10-second MP4 without storing user data.
GET /health does not expose MP4 or authentication state.
Set HOT_HAT_CLIP_SOURCE_TOKEN and HOT_HAT_CLIP_SOURCE_SMOKE_ID via Railway env; do not put credentials in Git.
This branch is isolated from new-dev and main. The service must verify a real video download before the game's backend uses it.
