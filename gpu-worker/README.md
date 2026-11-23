# SCRUM-65 · HOT-HAT GPU worker (Immers Cloud)

**Status:** infrastructure smoke only. This is NOT a complete matting / realtime
processing implementation. Do not enable it for users until Longpipe-XL + BiRefNet
quality, alpha-video codec, audio sync, authorization and latency tests pass.

This directory is isolated from the Railway API and browser matting pipeline.
It uses the rented **RTX 4090 / Ubuntu 24.04** instance and the
`pytorch/pytorch:2.5.1-cuda12.4-cudnn9-runtime` image already tested on that host.

## Deploy on the Immers VM

Prerequisites: Docker 29+, NVIDIA Container Toolkit, `docker run --gpus all`
successfully shows RTX 4090; `curl`, `openssl`, `git`.

```bash
git clone --branch new-dev https://github.com/yanisderbikov/hot-hat-back.git
cd hot-hat-back/gpu-worker
bash deploy.sh
```

For an existing clone, run `git pull --ff-only origin new-dev` before
`bash gpu-worker/deploy.sh` from the repository root.

Deployment generates a 64-character random token in `gpu-worker/.env`
(chmod 600 due to umask 077). **Never copy the token into GitHub/Trello/chat.**
The container binds **127.0.0.1:8088** on the host, NOT the public interface.
Do not open port 8088 on the public Internet.

Check local health:

```bash
curl -fsS http://127.0.0.1:8088/health
```

Test authenticated GPU access without printing the secret:

```bash
set -a; source .env; set +a
curl -fsS -H "X-Hot-Hat-GPU-Token: $HOT_HAT_GPU_TOKEN" http://127.0.0.1:8088/ready
curl -fsS -X POST -H "X-Hot-Hat-GPU-Token: $HOT_HAT_GPU_TOKEN" http://127.0.0.1:8088/v1/gpu/smoke
unset HOT_HAT_GPU_TOKEN
```

### Design constraints before production

1. **Priority 1:** asynchronous background removal on the original paid
   neuro-replacement MP4. Store original input and a separate transparent result;
   no second GenAPI call. Longpipe-XL temporal person + BiRefNet edge/prop
   models must be validated using real clips (hands, hair, moving keyboard).
2. **Priority 2:** opt-in automatic server fallback for slow client devices.
   Local matting remains default; consider latency and bandwidth before switching.
   Realtime sessions take priority over queue jobs; do not duplicate LiveKit tracks.
3. **Priority 3:** leave an extension point for future 3D kits. No implementation now.
4. Private communication to Railway: choose a TLS-protected authenticated gateway
   or tunnel *before* exposing the worker beyond localhost. Never store users'
   MP4s indefinitely. Apply bounded queues, size caps, timeouts and cleanup.
5. GPU idle state: verify Immers shelve/unshelve and lingering storage/IP charges
   before automating stop/start; `docker stop` does **not** stop the rental.
6. Performance acceptance: test 720p/30FPS, 1/5/10 cameras for quality, GPU VRAM,
   end-to-end latency and actual ruble cost. A successful smoke test proves only
   PyTorch CUDA, not Longpipe-XL/BiRefNet or actual video processing.

Tests (on a host with Python dependencies):

```bash
cd gpu-worker
python -m pip install -r requirements.txt pytest httpx
python -m pytest -q tests
```
