"""Minimal private GPU runtime for HOT-HAT (SCRUM-65).

This module intentionally exposes no production matting endpoint yet:
matching the existing Longpipe-XL + BiRefNet quality requires a tested model
pipeline. A successful GPU smoke test is NOT proof of video matting.
"""
import hmac
import os
import time

import torch
from fastapi import FastAPI, Header, HTTPException

app = FastAPI(title="HOT-HAT GPU Worker", version="0.1.0", docs_url=None, redoc_url=None, openapi_url=None)


def check_token(value: str | None) -> None:
    expected = os.environ.get("HOT_HAT_GPU_TOKEN", "")
    # Never run a remotely callable endpoint with an absent/example secret.
    if len(expected) < 32 or expected == "change-me-now":
        raise HTTPException(status_code=503, detail="Worker token is not configured")
    if not isinstance(value, str) or not hmac.compare_digest(expected, value):
        raise HTTPException(status_code=401, detail="Unauthorized")


@app.get("/health")
def health() -> dict:
    return {"status": "ok", "service": "hot-hat-gpu-worker"}


@app.get("/ready")
def ready(x_hot_hat_gpu_token: str | None = Header(default=None)) -> dict:
    check_token(x_hot_hat_gpu_token)
    if not torch.cuda.is_available():
        raise HTTPException(status_code=503, detail="CUDA unavailable")
    return {
        "status": "ready",
        "gpu": torch.cuda.get_device_name(0),
        "vram_total_mib": round(torch.cuda.get_device_properties(0).total_memory / (1024 * 1024)),
    }


@app.post("/v1/gpu/smoke")
def gpu_smoke(x_hot_hat_gpu_token: str | None = Header(default=None)) -> dict:
    check_token(x_hot_hat_gpu_token)
    if not torch.cuda.is_available():
        raise HTTPException(status_code=503, detail="CUDA unavailable")
    try:
        torch.cuda.synchronize()
        start = time.perf_counter()
        with torch.no_grad():
            matrix = torch.randn((1024, 1024), device="cuda")
            result = float((matrix @ matrix).sum().item())
        torch.cuda.synchronize()
        elapsed_ms = round((time.perf_counter() - start) * 1000, 2)
    except (RuntimeError, AssertionError) as exc:
        # Never echo internal exception details through the service.
        raise HTTPException(status_code=503, detail="GPU computation failed") from exc
    return {"ok": True, "gpu": torch.cuda.get_device_name(0), "elapsed_ms": elapsed_ms,
            "checksum": result}
