# HOT-HAT private clip source
Runs as a separate Railway service outside the main game's region.
It only accepts POST /clip with X-Hot-Hat-Clip-Token, validates a YouTube video ID and startSeconds, and returns a temporary 10-second MP4 without storing user data.
GET /health does not expose MP4 or authentication state.
Set HOT_HAT_CLIP_SOURCE_TOKEN and HOT_HAT_CLIP_SOURCE_SMOKE_ID via Railway env; do not put credentials in Git.
The authoritative code is mirrored into backend new-dev, and the private Railway extractor is deployed from that branch. The game still requires real MP4 smoke verification.


Age verified session option: [AGE_AUTH.md](AGE_AUTH.md) (disabled unless explicitly configured).
