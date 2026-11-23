import json
import subprocess
import numpy as np
from app import matting

def test_audio_and_alpha_webm_roundtrip_with_mocked_segmentation(tmp_path, monkeypatch):
    inp = tmp_path / "original.mp4"
    out = tmp_path / "transparent.webm"
    subprocess.run(["ffmpeg", "-nostdin", "-hide_banner", "-loglevel", "error", "-y",
        "-f", "lavfi", "-i", "testsrc2=size=160x90:rate=12:duration=1",
        "-f", "lavfi", "-i", "sine=frequency=440:duration=1",
        "-c:v", "mpeg4", "-c:a", "aac", "-shortest", str(inp)], check=True)
    class MockModel:
        def __init__(self, folder): pass
        def run(self, rgb): return np.full(rgb.shape[:2], 0.5, dtype=np.float32)
    monkeypatch.setattr(matting, "ModelPipeline", MockModel)
    info = matting.process(inp, out, tmp_path)
    assert info["frames"] == 12 and info["has_audio"] is True
    result = json.loads(subprocess.run(["ffprobe", "-v", "error", "-show_streams",
        "-of", "json", str(out)], check=True, text=True, capture_output=True).stdout)
    assert any(s.get("tags", {}).get("alpha_mode") == "1" for s in result["streams"])
    assert any(s["codec_type"] == "audio" for s in result["streams"])
