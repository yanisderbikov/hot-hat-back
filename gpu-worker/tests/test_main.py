"""Host-side contract tests; use pytest and httpx in a development venv."""
from fastapi.testclient import TestClient
from app.main import app


def test_health_does_not_expose_gpu_details():
    response = TestClient(app).get("/health")
    assert response.status_code == 200
    assert response.json() == {"status": "ok", "service": "hot-hat-gpu-worker"}


def test_token_required_for_smoke(monkeypatch):
    monkeypatch.setenv("HOT_HAT_GPU_TOKEN", "x" * 64)
    response = TestClient(app).post("/v1/gpu/smoke")
    assert response.status_code == 401


def test_example_token_rejected(monkeypatch):
    monkeypatch.setenv("HOT_HAT_GPU_TOKEN", "change-me-now")
    response = TestClient(app).post("/v1/gpu/smoke", headers={"X-Hot-Hat-GPU-Token": "change-me-now"})
    assert response.status_code == 503


def test_readiness_reports_absent_gpu(monkeypatch):
    monkeypatch.setenv("HOT_HAT_GPU_TOKEN", "x" * 64)
    monkeypatch.setattr("app.main.torch.cuda.is_available", lambda: False)
    response = TestClient(app).get("/ready", headers={"X-Hot-Hat-GPU-Token": "x" * 64})
    assert response.status_code == 503
