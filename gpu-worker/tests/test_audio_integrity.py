"""Regression: a three-second source must not lose its speech audio."""
import subprocess
import numpy as np
from app import matting


def test_preserve_three_second_audio(tmp_path, monkeypatch):
    source = tmp_path / "source.mp4"
    exported = tmp_path / "transparent.webm"
    subprocess.run([
        "ffmpeg", "-nostdin", "-v", "error", "-y",
        "-f", "lavfi", "-i", "testsrc2=size=160x90:rate=10:duration=3",
        "-f", "lavfi", "-i", "sine=frequency=440:duration=3",
        "-c:v", "mpeg4", "-c:a", "aac", "-shortest", str(source),
    ], check=True, timeout=30)

    class MockMatte:
        def __init__(self, model_dir):
            pass

        def run(self, frame):
            mask = np.zeros(frame.shape[:2], dtype=np.float32)
            mask[15:-15, 35:-35] = 1.0
            return mask

    monkeypatch.setattr(matting, "ModelPipeline", MockMatte)
    info = matting.process(source, exported, tmp_path)
    assert info["frames"] == 30
    assert info["source_audio_seconds"] >= 2.8
    assert info["export_audio_seconds"] >= 2.8


def test_audio_duration_parses_stream_tag():
    assert abs(matting.stream_duration({
        "tags": {"DURATION": "00:00:03.007000000"}
    }) - 3.007) < .0001
    assert matting.stream_duration({}) == 0.
