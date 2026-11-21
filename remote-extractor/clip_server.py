"""Isolated, private Railway clip source. No public video links or account credentials."""
import base64
import binascii
import hmac
import json
import os
import re
import subprocess
import tempfile
import threading
import time
from collections import OrderedDict
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path

ID = re.compile(r"^[A-Za-z0-9_-]{11}$")
TOKEN = os.environ.get("HOT_HAT_CLIP_SOURCE_TOKEN", "")
SEM = threading.BoundedSemaphore(2)
MAX_BYTES = 24 * 1024 * 1024
CACHE_MAX_BYTES = 24 * 1024 * 1024
CACHE_TTL_SECONDS = 240
_CACHE = OrderedDict()
_CACHE_LOCK = threading.Lock()


# Optional age-verified SESSION AUTH for restricted videos, NOT an OAuth login.
# A secure Railway service variable holds a Netscape cookie jar as base64.
# Never expose credentials through HTTP, browser JavaScript, logs, or Git.
AUTH_COOKIES_ENV = "HOT_HAT_YOUTUBE_COOKIES_B64"
AUTH_COOKIE_MAX_BYTES = 128 * 1024

def _authorized_cookie_file(temp_dir):
    encoded = os.environ.get(AUTH_COOKIES_ENV, "").strip()
    if not encoded:
        return None
    try:
        if len(encoded) > AUTH_COOKIE_MAX_BYTES * 2:
            raise ValueError("too large")
        raw = base64.b64decode(encoded, validate=True)
        if not raw or len(raw) > AUTH_COOKIE_MAX_BYTES:
            raise ValueError("invalid size")
        source = raw.decode("utf-8")
        if not (source.startswith("# Netscape HTTP Cookie File") or
                source.startswith("# HTTP Cookie File")):
            raise ValueError("invalid Netscape header")
        filtered = []
        for line in source.splitlines()[1:]:
            if not line or line.startswith("#") and not line.startswith("#HttpOnly_"):
                continue
            fields = line.split("\t")
            if len(fields) != 7:
                continue
            domain = fields[0].removeprefix("#HttpOnly_").strip().lower().lstrip(".")
            if domain not in ("youtube.com", "google.com") and not (
                    domain.endswith(".youtube.com") or domain.endswith(".google.com")):
                continue
            filtered.append(line)
        if not filtered:
            raise ValueError("no valid YouTube login cookies")
        dest = Path(temp_dir) / "age-verified-youtube-cookies.txt"
        dest.write_text("# Netscape HTTP Cookie File\n" + "\n".join(filtered) + "\n",
                        encoding="utf-8")
        dest.chmod(0o600)
        return dest
    except (UnicodeError, ValueError, binascii.Error):
        # Never echo even a prefix of an account cookie into service logs.
        print("CLIP_AGE_AUTH_CONFIGURATION_INVALID", flush=True)
        return None

def _is_age_gate(log):
    message = str(log).lower()
    return ("confirm your age" in message or "age-restricted" in message
            or "age restricted" in message)

def prepare_uncached(video_id, start_seconds):
    if not ID.fullmatch(str(video_id)):
        raise ValueError("invalid YouTube ID")
    start = float(start_seconds)
    if not (0 <= start <= 7200) or start != start:
        raise ValueError("invalid start seconds")
    with tempfile.TemporaryDirectory(prefix="hat-youtube-") as temp:
        command = [
            "/opt/yt-dlp/bin/yt-dlp",
            "--ignore-config", "--no-playlist", "--force-ipv4",
            "--no-progress", "--no-part", "--socket-timeout", "10",
            "--retries", "1", "--fragment-retries", "1",
            "--concurrent-fragments", "1", "--js-runtimes", "node",
            "--download-sections", "*%.3f-%.3f" % (start, start + 10),
            "--force-keyframes-at-cuts",
            # FFmpegFD normally re-encodes selected 10s with its default slow
            # preset. Use an explicitly selected fast encoding only when
            # enabled by a Railway variable; retain exact frame cuts.
            *(["--downloader-args", "ffmpeg_o:-preset ultrafast"]
              if os.environ.get("HOT_HAT_CLIP_FAST_ENCODING") == "1" else []),
            "--format", "b[ext=mp4][height<=480]/b[height<=480]/bv*[height<=480]+ba/b",
            "--merge-output-format", "mp4", "--recode-video", "mp4",
            "--limit-rate", "2M",
            "--output", str(Path(temp) / "clip.%(ext)s"),
            "https://www.youtube.com/watch?v=" + video_id
        ]
        finished = subprocess.run(command, stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
                                  timeout=70, check=False, text=True)
        # Public extraction remains anonymous and fast. A genuine age gate
        # retries with the securely provisioned verified YouTube session.
        # Logging into YouTube in the viewer's browser does NOT log Railway in.
        if finished.returncode and _is_age_gate(finished.stdout):
            cookie_file = _authorized_cookie_file(temp)
            if cookie_file:
                print("CLIP_AGE_AUTH_RETRY videoId=%s" % video_id, flush=True)
                authenticated = command[:-1] + ["--cookies", str(cookie_file), command[-1]]
                finished = subprocess.run(authenticated, stdout=subprocess.PIPE,
                                          stderr=subprocess.STDOUT, timeout=70,
                                          check=False, text=True)
                if finished.returncode == 0:
                    print("CLIP_AGE_AUTH_SUCCESS videoId=%s" % video_id, flush=True)
                else:
                    print("CLIP_AGE_AUTH_REFUSED videoId=%s" % video_id, flush=True)
        if finished.returncode:
            message = finished.stdout[-1300:]
            code = ("YOUTUBE_BOT_CONFIRMATION_REQUIRED" if "not a bot" in message
                    else "YOUTUBE_AGE_CONFIRMATION_REQUIRED" if _is_age_gate(message)
                    else "YOUTUBE_IMPORT_UNAVAILABLE")
            raise RuntimeError(code + ": " + message[-350:])
        clips = sorted(Path(temp).glob("clip*.mp4"), key=lambda p: p.stat().st_size, reverse=True)
        if not clips:
            raise RuntimeError("YOUTUBE_IMPORT_UNAVAILABLE: no mp4 generated")
        clip = clips[0].read_bytes()
        if len(clip) < 2048 or len(clip) > MAX_BYTES:
            raise RuntimeError("YOUTUBE_IMPORT_UNAVAILABLE: invalid mp4 size")
        return clip

def prepare(video_id, start_seconds):
    # Reuse an exact 10-second MP4 across different rooms and backend pods.
    # Cache no URLs/tokens/account data; bounded by TTL and total byte size.
    key = (str(video_id), round(float(start_seconds) * 1000))
    now = time.monotonic()
    with _CACHE_LOCK:
        for existing, item in list(_CACHE.items()):
            if item[0] <= now:
                del _CACHE[existing]
        hit = _CACHE.get(key)
        if hit is not None:
            _CACHE.move_to_end(key)
            print("CLIP_CACHE_HIT videoId=%s startMs=%s bytes=%s" %
                  (video_id, key[1], len(hit[1])), flush=True)
            return hit[1]
    clip = prepare_uncached(video_id, start_seconds)
    with _CACHE_LOCK:
        _CACHE[key] = (time.monotonic() + CACHE_TTL_SECONDS, clip)
        _CACHE.move_to_end(key)
        size = sum(len(item[1]) for item in _CACHE.values())
        while len(_CACHE) > 20 or size > CACHE_MAX_BYTES:
            _, removed = _CACHE.popitem(last=False)
            size -= len(removed[1])
    return clip

class Handler(BaseHTTPRequestHandler):
    def answer(self, status, data, mime="application/json"):
        self.send_response(status)
        self.send_header("Content-Type", mime)
        self.send_header("Content-Length", str(len(data)))
        self.send_header("Cache-Control", "no-store")
        self.send_header("X-Content-Type-Options", "nosniff")
        self.end_headers()
        self.wfile.write(data)
    def do_GET(self):
        if self.path == "/health":
            return self.answer(200, b'{"ok":true}')
        return self.answer(404, b'{"error":"not found"}')
    def do_POST(self):
        if self.path != "/clip":
            return self.answer(404, b'{"error":"not found"}')
        received = self.headers.get("X-Hot-Hat-Clip-Token", "")
        if not TOKEN or not hmac.compare_digest(received, TOKEN):
            return self.answer(401, b'{"error":"unauthorized"}')
        try:
            length = int(self.headers.get("Content-Length", "-1"))
            if length < 1 or length > 1000:
                return self.answer(400, b'{"error":"bad request"}')
            payload = json.loads(self.rfile.read(length))
            video_id = payload["videoId"]
            start = payload.get("startSeconds", 0)
            if not SEM.acquire(blocking=False):
                return self.answer(503, b'{"error":"busy"}')
            try:
                started_at = time.monotonic()
                clip = prepare(video_id, start)
            finally:
                SEM.release()
            elapsed_ms = int((time.monotonic() - started_at) * 1000)
            print("CLIP_OK videoId=%s start=%s bytes=%s elapsedMs=%s" %
                  (video_id, start, len(clip), elapsed_ms), flush=True)
            return self.answer(200, clip, "video/mp4")
        except ValueError as e:
            return self.answer(400, json.dumps({"error":str(e)}).encode())
        except subprocess.TimeoutExpired:
            return self.answer(504, b'{"error":"extractor timeout"}')
        except Exception as e:
            msg = str(e)
            print("CLIP_FAILED " + msg[:500], flush=True)
            code = msg.partition(":")[0]
            return self.answer(422, json.dumps({"code":code,"error":"source unavailable"}).encode())

def smoke():
    video_id = os.environ.get("HOT_HAT_CLIP_SOURCE_SMOKE_ID", "").strip()
    if not video_id: return
    print("CLIP_SOURCE_SMOKE_START id=" + video_id,flush=True)
    started_at = time.monotonic()
    try:
        clip = prepare(video_id, 0)
        print("CLIP_SOURCE_SMOKE_OK id=%s bytes=%s elapsedMs=%s" %
              (video_id,len(clip),int((time.monotonic()-started_at)*1000)),flush=True)
    except Exception as e:
        print("CLIP_SOURCE_SMOKE_FAILED id=%s reason=%s" % (video_id,str(e)[:400]),flush=True)

if __name__ == "__main__":
    port = int(os.environ.get("PORT", "8080"))
    server = ThreadingHTTPServer(("0.0.0.0",port), Handler)
    threading.Thread(target=smoke, daemon=True).start()
    print("CLIP_SOURCE_STARTED port=%s" % port, flush=True)
    server.serve_forever()
