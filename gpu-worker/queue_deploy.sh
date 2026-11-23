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
# Probe the exact backend health endpoint before replacing the existing queue.
# A Railway app edge can be unreachable for TLS from Immers even when other
# HTTPS destinations work. Find a reachable Railway edge dynamically, while
# preserving the original hostname, SNI and TLS certificate verification.
RAILWAY_HOST='hot-hat-back.up.railway.app'
RAILWAY_HEALTH="https://${RAILWAY_HOST}/api/v2/app/health"
command -v curl >/dev/null || { echo 'curl is required for safe Railway route preflight' >&2; exit 1; }
route_args=()
if curl --noproxy '*' -4 -fsS --connect-timeout 4 --max-time 8 \
    --output /dev/null "$RAILWAY_HEALTH" 2>/dev/null; then
  echo 'Railway default HTTPS route is healthy; no override needed.'
else
  echo 'Railway default HTTPS route failed; probing TLS-verified Railway edge alternatives...'
  command -v getent >/dev/null || { echo 'getent is required for Railway edge discovery' >&2; exit 1; }
  mapfile -t edge_ips < <(getent ahostsv4 railway.com | awk '$2 == "STREAM" {print $1}' | sort -u)
  chosen_edge=''
  for edge_ip in "${edge_ips[@]}"; do
    # IPv4 input comes from DNS; reject non-IPv4 strings before constructing --add-host.
    if [[ ! "$edge_ip" =~ ^[0-9]{1,3}(\.[0-9]{1,3}){3}$ ]]; then
      continue
    fi
    if curl --noproxy '*' -4 -fsS --connect-timeout 4 --max-time 8 \
        --resolve "${RAILWAY_HOST}:443:${edge_ip}" \
        --output /dev/null "$RAILWAY_HEALTH" 2>/dev/null; then
      chosen_edge="$edge_ip"
      break
    fi
  done
  if [[ -z "$chosen_edge" ]]; then
    echo 'No reachable TLS-verified Railway edge. Existing GPU queue was not replaced.' >&2
    exit 1
  fi
  route_args=(--add-host "${RAILWAY_HOST}:${chosen_edge}")
  echo "Railway fallback route verified: ${RAILWAY_HOST} -> ${chosen_edge} (TLS CA/SNI kept)."
  echo 'This mapping is temporary for this container; rerun queue_deploy.sh if the edge changes.'
fi
sudo docker rm -f hot-hat-gpu-queue >/dev/null 2>&1 || true
sudo docker run -d --name hot-hat-gpu-queue --restart unless-stopped \
  "${route_args[@]}" \
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
  echo 'No confirmed Railway 200. Diagnosing outbound network without machine token...' >&2
  sudo docker exec hot-hat-gpu-queue python -m app.diagnose_relay 2>&1 || true
  if command -v curl >/dev/null 2>&1; then
    echo 'Checking Railway public /health from the VM host (IPv4, no bearer secret):'
    curl -4 --silent --show-error --connect-timeout 5 --max-time 14 \
      --output /dev/null \
      --write-out 'host_curl: HTTP %{http_code} remote_ip=%{remote_ip} tcp=%{time_connect}s tls=%{time_appconnect}s total=%{time_total}s\\n' \
      'https://hot-hat-back.up.railway.app/api/v2/app/health' || true
  fi
  echo 'GPU queue has not confirmed Railway HTTP 200; no GenAPI payment has been made.' >&2
  exit 1
fi
echo 'GPU queue is HEALTHY: authorized Railway queue poll confirmed; no paid request made.'
