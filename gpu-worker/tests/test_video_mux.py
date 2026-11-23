import json
import subprocess

import numpy as np

from app import matting
from app.inspect_alpha import inspect


def test_audio_and_real_transparency_roundtrip_with_mocked_segmentation(tmp_path, monkeypatch):
    """Real FFmpeg encoding/decoding with a fake rectangular mask (NO CUDA test)."""
    inp = tmp_path / "original.mp4"
    out = tmp_path / "transparent.webm"
    subprocess.run([
        "ffmpeg", "-nostdin", "-hide_banner", "-loglevel", "error", "-y",
        "-f", "lavfi", "-i", "testsrc2=size=160x90:rate=12:duration=1",
        "-f", "lavfi", "-i", "sine=frequency=440:duration=1",
        "-c:v", "mpeg4", "-c:a", "aac", "-shortest", str(inp),
    ], check=True)

    class MockModel:
        def __init__(self, folder):
            pass
        def run(self, rgb):
            h, w = rgb.shape[:2]
            mask = np.zeros((h, w), dtype=np.float32)
            mask[h // 4:3 * h // 4, w // 4:3 * w // 4] = 1
            return mask

    monkeypatch.setattr(matting, "ModelPipeline", MockModel)
    info = matting.process(inp, out, tmp_path)
    assert info["frames"] == 12 and info["has_audio"] is True
    result = json.loads(subprocess.run([
        "ffprobe", "-v", "error", "-show_streams", "-of", "json", str(out)
    ], check=True, text=True, capture_output=True).stdout)
    assert any(s.get("tags", {}).get("alpha_mode") == "1" for s in result["streams"])
    assert any(s["codec_type"] == "audio" for s in result["streams"])
    preview = tmp_path / "preview.png"
    metrics = inspect(out, preview)
    assert metrics["transparent_pct"] > 20
    assert metrics["opaque_pct"] > 10
    assert preview.is_file() and preview.stat().st_size > 100
