"""Fetch only the known official ONNX model sources into a mounted private folder."""
import hashlib
import json
import os
import shutil
from pathlib import Path
from huggingface_hub import hf_hub_download

FILES = [
    ("Longpipe/longpipe", "onnx/xl/matting_landscape.onnx", "longpipe-xl-matting.onnx"),
    ("Longpipe/longpipe", "onnx/xl/flow_landscape.onnx", "longpipe-xl-flow.onnx"),
    ("onnx-community/BiRefNet_lite-ONNX", "onnx/model_fp16.onnx", "birefnet-lite-fp16.onnx"),
]

def main():
    target = Path(os.environ.get("HOT_HAT_MODELS_DIR", "/models")).resolve()
    target.mkdir(parents=True, exist_ok=True)
    checksums = {}
    for repository, filename, name in FILES:
        output = target / name
        if not output.is_file() or output.stat().st_size < 100_000:
            print(f"Downloading {repository}/{filename}", flush=True)
            fetched = Path(hf_hub_download(repo_id=repository, filename=filename, local_dir=str(target)))
            shutil.move(str(fetched), str(output))
        with output.open("rb") as file:
            digest = hashlib.file_digest(file, "sha256").hexdigest()
        checksums[name] = {"bytes": output.stat().st_size, "sha256": digest,
                           "source": f"{repository}/{filename}"}
    print(json.dumps(checksums, indent=2), flush=True)

if __name__ == "__main__":
    main()
