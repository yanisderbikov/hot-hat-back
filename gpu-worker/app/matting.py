"""SCRUM-65: GPU-only Longpipe XL + BiRefNet Lite MP4 alpha processing.

Offline-only stage of neuro replacement. No GenAPI calls. Never edits original MP4.
Fails closed if ONNX CUDA/models/audio preservation are unavailable.
"""
from __future__ import annotations
import argparse
import json
import subprocess
import tempfile
from fractions import Fraction
from pathlib import Path
import cv2
import numpy as np

LP_SIZE = (1280, 768)
BR_SIZE = (1024, 1024)
MEAN = np.array([.485, .456, .406], dtype=np.float32).reshape(1, 1, 3)
STD = np.array([.229, .224, .225], dtype=np.float32).reshape(1, 1, 3)
MAX_SIZE = 100 * 1024 * 1024
MAX_SECONDS = 15

class MattingError(RuntimeError):
    pass

def probe(source: Path):
    if not source.is_file() or not 1200 <= source.stat().st_size <= MAX_SIZE:
        raise MattingError("MP4 source is missing or too large")
    try:
        p = subprocess.run(["ffprobe", "-v", "error", "-show_streams", "-show_format",
                            "-of", "json", str(source)], check=True, capture_output=True,
                           text=True, timeout=30)
        data = json.loads(p.stdout)
        videos = [s for s in data["streams"] if s["codec_type"] == "video"]
        if len(videos) != 1: raise MattingError("Expected exactly one video")
        video = videos[0]
        w, h = int(video["width"]), int(video["height"])
        duration = float(video.get("duration") or data["format"].get("duration") or 0)
        fps = float(Fraction(video.get("avg_frame_rate", "0/1")))
        if w <= 0 or h <= 0 or w * h > 1920 * 1080:
            raise MattingError("Video dimensions outside limit")
        if not 0 < duration <= MAX_SECONDS or not 5 <= fps <= 120:
            raise MattingError("Video FPS/duration outside limit")
        audio = any(s["codec_type"] == "audio" for s in data["streams"])
        return w, h, min(30., fps), audio
    except (ValueError, KeyError, ZeroDivisionError, subprocess.SubprocessError) as e:
        raise MattingError("Invalid MP4 metadata") from e

def tensor(rgb: np.ndarray, dims: tuple[int, int], biref=False):
    pixels = cv2.resize(rgb, dims, interpolation=cv2.INTER_LINEAR).astype(np.float32) / 255.
    if biref: pixels = (pixels - MEAN) / STD
    return np.ascontiguousarray(pixels.transpose(2, 0, 1)[None], dtype=np.float32)

def alpha_prob(data: np.ndarray, logits=False):
    x = np.asarray(data).reshape(-1, data.shape[-2], data.shape[-1])[0].astype(np.float32)
    if not np.isfinite(x).all(): raise MattingError("Non-finite model mask")
    if logits: x = 1 / (1 + np.exp(-np.clip(x, -30, 30)))
    if x.min() < -.01 or x.max() > 1.01: raise MattingError("Invalid alpha range")
    return np.clip(x, 0, 1)

def warp(previous: np.ndarray, flow_raw: np.ndarray):
    h, w = previous.shape
    f = np.asarray(flow_raw).reshape(2, flow_raw.shape[-2], flow_raw.shape[-1])
    fx = cv2.resize(f[0], (w, h), interpolation=cv2.INTER_LINEAR) * (w / 320.)
    fy = cv2.resize(f[1], (w, h), interpolation=cv2.INTER_LINEAR) * (h / 192.)
    yy, xx = np.indices((h, w), dtype=np.float32)
    return cv2.remap(previous, xx + fx, yy + fy, cv2.INTER_LINEAR,
                     borderMode=cv2.BORDER_CONSTANT, borderValue=0)

def fuse(person, detail, rgb, previous_rgb):
    h, w = person.shape
    detailed = cv2.resize(detail, (w, h), interpolation=cv2.INTER_LINEAR)
    subject = (person >= .60).astype(np.uint8)
    if subject.sum() < max(120, int(h * w * .004)):
        raise MattingError("Missing person; refusing to export bad mask")
    close = cv2.dilate(subject, np.ones((5, 5), np.uint8)) != 0
    out = np.maximum(person, np.where(close, .90 * detailed, 0.))
    if previous_rgb is not None:
        delta = np.max(np.abs(rgb.astype(np.int16) - previous_rgb.astype(np.int16)), axis=2)
        candidates = (detailed >= .70) & (delta >= 35) & (person < .60)
        if candidates.sum() <= w * h * .20:
            _, labels = cv2.connectedComponents((candidates | (subject != 0)).astype(np.uint8),
                                                  connectivity=4)
            touching = np.unique(labels[subject != 0])
            touching = touching[touching != 0]
            graft = candidates & np.isin(labels, touching)
            out = np.maximum(out, np.where(graft, detailed, 0.))
    return np.clip(out, 0, 1)

class ModelPipeline:
    def __init__(self, folder: Path):
        import torch   # Load CUDA/cuDNN shared libraries for ONNX Runtime.
        import onnxruntime as ort
        if not torch.cuda.is_available() or "CUDAExecutionProvider" not in ort.get_available_providers():
            raise MattingError("ONNX CUDA is not available")
        paths = {"person": folder / "longpipe-xl-matting.onnx",
                 "flow": folder / "longpipe-xl-flow.onnx",
                 "detail": folder / "birefnet-lite-fp16.onnx"}
        if not all(p.is_file() and p.stat().st_size > 100_000 for p in paths.values()):
            raise MattingError("Missing official model files; run app.download_models")
        self.sessions = {name: ort.InferenceSession(str(path), providers=["CUDAExecutionProvider"])
                         for name, path in paths.items()}
        for name, session in self.sessions.items():
            if "CUDAExecutionProvider" not in session.get_providers():
                raise MattingError(f"{name} cannot execute via CUDA")
        self.prev_x = self.prev_mask = self.prev_rgb = None

    def run(self, rgb: np.ndarray):
        h, w = rgb.shape[:2]
        x = tensor(rgb, LP_SIZE)
        sess = self.sessions["person"]
        raw = sess.run(None, {sess.get_inputs()[0].name: x})[0]
        person = cv2.resize(alpha_prob(raw), (w, h), interpolation=cv2.INTER_LINEAR)
        if self.prev_x is not None:
            flow = self.sessions["flow"]
            flow_raw = flow.run(None, {flow.get_inputs()[0].name:
                                           np.concatenate([self.prev_x, x], axis=1)})[0]
            prior = warp(self.prev_mask, flow_raw)
            agree = np.exp(-np.abs(person - prior) * 15)
            weight = .32 * agree
            person = person * (1 - weight) + prior * weight
        self.prev_x, self.prev_mask = x, person.copy()
        detail_sess = self.sessions["detail"]
        raw_detail = detail_sess.run(None, {detail_sess.get_inputs()[0].name:
                                           tensor(rgb, BR_SIZE, biref=True)})[0]
        fine = alpha_prob(raw_detail, logits=True)
        result = fuse(person, fine, rgb, self.prev_rgb)
        self.prev_rgb = rgb.copy()
        return result

def process(source: Path, output: Path, model_dir: Path):
    source, output = source.resolve(), output.resolve()
    if output == source or output.suffix.lower() != ".webm" or output.exists():
        raise MattingError("Output must be a NEW .webm file")
    w, h, fps, has_audio = probe(source)
    models = ModelPipeline(model_dir.resolve())  # No inference without models.
    partial = output.with_suffix(".webm.partial")
    if partial.exists(): raise MattingError("Partial output already exists")
    decode = ["ffmpeg", "-nostdin", "-hide_banner", "-loglevel", "error", "-i", str(source),
              "-map", "0:v:0", "-vf", f"fps={fps:.6f}", "-pix_fmt", "rgb24",
              "-f", "rawvideo", "pipe:1"]
    encode = ["ffmpeg", "-nostdin", "-hide_banner", "-loglevel", "error", "-y",
              "-f", "rawvideo", "-pix_fmt", "rgba", "-s", f"{w}x{h}", "-r", f"{fps:.6f}",
              "-i", "pipe:0", "-i", str(source), "-map", "0:v:0"]
    if has_audio:
        encode += ["-map", "1:a:0", "-c:a", "libopus", "-b:a", "128k"]
    encode += ["-c:v", "libvpx-vp9", "-pix_fmt", "yuva420p", "-auto-alt-ref", "0",
               "-crf", "23", "-b:v", "0", "-deadline", "good", "-cpu-used", "3",
               "-shortest", "-f", "webm", str(partial)]
    count = 0
    with tempfile.TemporaryFile() as decode_log, tempfile.TemporaryFile() as encode_log:
        reader = subprocess.Popen(decode, stdout=subprocess.PIPE, stderr=decode_log)
        writer = subprocess.Popen(encode, stdin=subprocess.PIPE, stderr=encode_log)
        try:
            size = w * h * 3
            limit = int(MAX_SECONDS * fps) + 2
            while count < limit:
                data = reader.stdout.read(size)
                if not data: break
                if len(data) != size: raise MattingError("Truncated frame")
                rgb = np.frombuffer(data, np.uint8).reshape(h, w, 3).copy()
                matte = (models.run(rgb) * 255).clip(0, 255).astype(np.uint8)
                writer.stdin.write(np.dstack((rgb, matte)).tobytes())
                count += 1
            if count == 0 or count >= limit: raise MattingError("Frame count out of range")
            writer.stdin.close()
            reader.stdout.close()
            if reader.wait(timeout=30) or writer.wait(timeout=180):
                raise MattingError("ffmpeg decoding/encoding failed")
            meta = json.loads(subprocess.run(
                ["ffprobe", "-v", "error", "-show_streams", "-of", "json", str(partial)],
                check=True, capture_output=True, text=True, timeout=30).stdout)
            video = next((s for s in meta["streams"] if s["codec_type"] == "video"), None)
            if not video or video.get("codec_name") != "vp9" or video.get("tags", {}).get("alpha_mode") != "1":
                raise MattingError("WebM alpha channel missing")
            if has_audio and not any(s["codec_type"] == "audio" for s in meta["streams"]):
                raise MattingError("Audio was lost")
            partial.rename(output)
            return {"frames": count, "width": w, "height": h, "fps": fps,
                    "has_audio": has_audio, "format": "VP9 WebM alpha"}
        finally:
            if writer.stdin and not writer.stdin.closed: writer.stdin.close()
            if reader.poll() is None: reader.kill(); reader.wait()
            if writer.poll() is None: writer.kill(); writer.wait()
            partial.unlink(missing_ok=True)

if __name__ == "__main__":
    p = argparse.ArgumentParser(description="Longpipe XL + BiRefNet GPU alpha matting")
    p.add_argument("--input", type=Path, required=True)
    p.add_argument("--output", type=Path, required=True)
    p.add_argument("--models", type=Path, default=Path("/models"))
    args = p.parse_args()
    try:
        print(json.dumps(process(args.input, args.output, args.models)), flush=True)
    except MattingError as e:
        p.error(str(e))
