"""Isolated, private Railway clip source. No public video links or account credentials."""
import hmac
import json
import os
import re
import subprocess
import tempfile
import threading
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path

ID = re.compile(r"^[A-Za-z0-9_-]{11}$")
TOKEN = os.environ.get("HOT_HAT_CLIP_SOURCE_TOKEN", "")
SEM = threading.BoundedSemaphore(2)
MAX_BYTES = 24 * 1024 * 1024

def prepare(video_id, start_seconds):
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
            "--format", "b[ext=mp4][height<=480]/b[height<=480]/bv*[height<=480]+ba/b",
            "--merge-output-format", "mp4", "--recode-video", "mp4",
            "--limit-rate", "2M",
            "--output", str(Path(temp) / "clip.%(ext)s"),
            "https://www.youtube.com/watch?v=" + video_id
        ]
        finished = subprocess.run(command, stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
                                  timeout=70, check=False, text=True)
        if finished.returncode:
            message = finished.stdout[-1300:]
            code = ("YOUTUBE_BOT_CONFIRMATION_REQUIRED" if "not a bot" in message
                    else "YOUTUBE_AGE_CONFIRMATION_REQUIRED" if ("age" in message.lower() and
                          ("sign in" in message.lower() or "confirm" in message.lower()))
                    else "YOUTUBE_IMPORT_UNAVAILABLE")
            raise RuntimeError(code + ": " + message[-350:])
        clips = sorted(Path(temp).glob("clip*.mp4"), key=lambda p: p.stat().st_size, reverse=True)
        if not clips:
            raise RuntimeError("YOUTUBE_IMPORT_UNAVAILABLE: no mp4 generated")
        clip = clips[0].read_bytes()
        if len(clip) < 2048 or len(clip) > MAX_BYTES:
            raise RuntimeError("YOUTUBE_IMPORT_UNAVAILABLE: invalid mp4 size")
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
                clip = prepare(video_id, start)
            finally:
                SEM.release()
            print("CLIP_OK videoId=%s start=%s bytes=%s" % (video_id, start, len(clip)), flush=True)
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
    try:
        clip = prepare(video_id, 0)
        print("CLIP_SOURCE_SMOKE_OK id=%s bytes=%s" % (video_id,len(clip)),flush=True)
    except Exception as e:
        print("CLIP_SOURCE_SMOKE_FAILED id=%s reason=%s" % (video_id,str(e)[:400]),flush=True)

if __name__ == "__main__":
    port = int(os.environ.get("PORT", "8080"))
    server = ThreadingHTTPServer(("0.0.0.0",port), Handler)
    threading.Thread(target=smoke, daemon=True).start()
    print("CLIP_SOURCE_STARTED port=%s" % port, flush=True)
    server.serve_forever()
