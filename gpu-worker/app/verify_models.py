"""SCRUM-65: verify ONNX model CUDA inference and measure warmed model latency.

This is a model-only benchmark. It excludes video decoding, temporal fusion,
encoding, and network transport; it MUST NOT be cited as actual game FPS.
"""
import argparse
import json
import statistics
import time
from pathlib import Path

import numpy as np

from app.matting import ModelPipeline, MattingError, LP_SIZE, BR_SIZE


def p95(values: list[float]) -> float:
    ordered = sorted(values)
    index = (len(ordered) - 1) * .95
    low = int(index)
    upper = min(low + 1, len(ordered) - 1)
    return ordered[low] + (ordered[upper] - ordered[low]) * (index - low)


def check(repeats: int = 7) -> dict:
    if not 2 <= repeats <= 30:
        raise ValueError("repeats must be between 2 and 30")
    pipeline = ModelPipeline(Path("/models"))
    checks = {}
    for name, size, channels in (("person", LP_SIZE, 3), ("flow", LP_SIZE, 6),
                                 ("detail", BR_SIZE, 3)):
        session = pipeline.sessions[name]
        arg = session.get_inputs()[0]
        output = session.get_outputs()[0]
        data = np.zeros((1, channels, size[1], size[0]), dtype=np.float32)
        measurements = []
        output_shape = None
        for attempt in range(repeats + 2):
            start = time.perf_counter()
            result = session.run(None, {arg.name: data})[0]
            elapsed_ms = (time.perf_counter() - start) * 1000
            if result.size == 0 or not np.isfinite(result).all():
                raise MattingError(f"{name} produced invalid values")
            output_shape = list(result.shape)
            if attempt >= 2:
                measurements.append(elapsed_ms)
        checks[name] = {
            "provider": session.get_providers()[0],
            "input": list(data.shape),
            "output": output_shape,
            "repeat_count_after_warmup": len(measurements),
            "median_ms": round(statistics.median(measurements), 2),
            "p95_ms": round(p95(measurements), 2),
            "min_ms": round(min(measurements), 2),
            "output_name": output.name
        }
    return {
        "models_cuda_inference_ok": True,
        "model_only_benchmark": True,
        "not_end_to_end_video_fps": True,
        "checks": checks,
    }


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--repeats", type=int, default=7)
    args = parser.parse_args()
    print(json.dumps(check(args.repeats), indent=2), flush=True)
