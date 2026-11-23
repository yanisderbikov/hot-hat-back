"""Inspect a real exported VP9+alpha WebM and produce a checkerboard preview PNG.

Uses libvpx-vp9 explicitly: ffmpeg's native VP9 decoder may drop alpha.
This is a structural validation, NOT a human evaluation of the mask.
"""
from __future__ import annotations
import argparse
import json
import subprocess
from pathlib import Path

import cv2
import numpy as np
from app.matting import stream_duration


def inspect(video: Path, preview: Path) -> dict:
    if not video.is_file() or not 500 <= video.stat().st_size <= 120_000_000:
        raise ValueError("Missing or invalid WebM export")
    streams = json.loads(subprocess.run([
        "ffprobe", "-v", "error", "-show_streams", "-of", "json", str(video)
    ], capture_output=True, text=True, check=True, timeout=30).stdout)["streams"]
    v = next((s for s in streams if s.get("codec_type") == "video"), None)
    if not v or v.get("codec_name") != "vp9" or v.get("tags", {}).get("alpha_mode") != "1":
        raise ValueError("Video is not VP9 with alpha_mode=1")
    if not any(s.get("codec_type") == "audio" for s in streams):
        raise ValueError("Video has lost its audio stream")
    w, h = int(v["width"]), int(v["height"])
    result = subprocess.run([
        "ffmpeg", "-nostdin", "-hide_banner", "-loglevel", "error",
        "-c:v", "libvpx-vp9", "-i", str(video),
        "-frames:v", "1", "-f", "rawvideo", "-pix_fmt", "rgba", "pipe:1"
    ], stdout=subprocess.PIPE, stderr=subprocess.PIPE, check=True, timeout=45)
    if len(result.stdout) != w * h * 4:
        raise ValueError("Could not decode transparent RGBA frame")
    rgba = np.frombuffer(result.stdout, dtype=np.uint8).reshape(h, w, 4)
    alpha = rgba[:, :, 3]
    stats = {
        "width": w, "height": h,
        "alpha_min": int(alpha.min()), "alpha_max": int(alpha.max()),
        "transparent_pct": round(float(np.mean(alpha < 25) * 100), 2),
        "opaque_pct": round(float(np.mean(alpha > 230) * 100), 2),
        "audio_present": True, "vp9_alpha_metadata": True,
        "video_seconds": stream_duration(v),
        "audio_seconds": stream_duration(next(s for s in streams if s["codec_type"] == "audio")),
    }
    if stats["transparent_pct"] < .5 or stats["opaque_pct"] < .5:
        raise ValueError(f"Mask appears blank or all opaque/transparent: {stats}")
    yy, xx = np.indices((h, w))
    chess = np.where((xx // 20 + yy // 20) % 2 == 0, 190, 105)
    bg = np.repeat(chess[:, :, None], 3, axis=2).astype(np.float32)
    factor = alpha.astype(np.float32)[:, :, None] / 255.0
    rgb = rgba[:, :, :3].astype(np.float32) * factor + bg * (1 - factor)
    preview.parent.mkdir(parents=True, exist_ok=True)
    if not cv2.imwrite(str(preview), cv2.cvtColor(rgb.astype(np.uint8), cv2.COLOR_RGB2BGR)):
        raise ValueError("Could not write checkerboard preview PNG")
    stats["preview"] = str(preview)
    return stats


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--input", required=True, type=Path)
    parser.add_argument("--preview", required=True, type=Path)
    args = parser.parse_args()
    print(json.dumps(inspect(args.input, args.preview), indent=2), flush=True)
