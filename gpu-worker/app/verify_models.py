"""One-shot GPU verification of ALL three ONNX models without uploading a user clip.

Use after download_models.sh. Verifies actual inference and output contracts,
not real-video visual quality or real-time throughput.
"""
import json
import time
from pathlib import Path
import numpy as np

from app.matting import ModelPipeline, MattingError, LP_SIZE, BR_SIZE

def check() -> dict:
    pipeline = ModelPipeline(Path("/models"))
    checks = {}
    for name, size, channels in (("person", LP_SIZE, 3), ("flow", LP_SIZE, 6),
                                 ("detail", BR_SIZE, 3)):
        session = pipeline.sessions[name]
        arg = session.get_inputs()[0]
        output = session.get_outputs()[0]
        # Correct shape and valid ONNX computation with CUDAExecutionProvider.
        data = np.zeros((1, channels, size[1], size[0]), dtype=np.float32)
        t = time.perf_counter()
        result = session.run(None, {arg.name: data})[0]
        duration = round((time.perf_counter() - t) * 1000, 2)
        if result.size == 0 or not np.isfinite(result).all():
            raise MattingError(f"{name} produced invalid values")
        checks[name] = {
            "provider": session.get_providers()[0],
            "input": list(data.shape),
            "output": list(result.shape),
            "elapsed_ms": duration,
            "output_name": output.name
        }
    return {"models_cuda_inference_ok": True, "checks": checks}

if __name__ == "__main__":
    print(json.dumps(check(), indent=2), flush=True)
