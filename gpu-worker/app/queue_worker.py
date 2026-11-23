"""Immers RTX 4090: private outbound-only queue consumer for neuro replacement.

Only matting happens here: NEVER GenAPI, lip sync, voice changes or player GPU.
Input is already a completed Wan MP4 with the synthesized voice attached.
"""
import os
import re
import signal
import time
from pathlib import Path
from urllib.parse import urlparse

import requests

from app.matting import MattingError, process
from app.inspect_alpha import inspect

BASE=os.getenv("HOT_HAT_GPU_SERVER_BASE_URL","https://hot-hat-back.up.railway.app").rstrip("/")
TOKEN=os.getenv("HOT_HAT_GPU_TOKEN","")
PATH=BASE+"/api/v2/gpu/neuro"
STOP=False

def stop(*_):
    global STOP
    STOP=True

signal.signal(signal.SIGTERM,stop)
signal.signal(signal.SIGINT,stop)

def make_client():
    parsed=urlparse(BASE)
    if parsed.scheme!="https" or not parsed.hostname or parsed.username or parsed.password:
        raise RuntimeError("GPU_SERVER_BASE_URL_MUST_BE_HTTPS")
    if len(TOKEN)<32:
        raise RuntimeError("GPU_MACHINE_TOKEN_NOT_CONFIGURED")
    s=requests.Session()
    s.headers.update({"X-Hot-Hat-Gpu-Token":TOKEN})
    return s

def handle(s,job):
    if not re.fullmatch(r"[0-9a-f-]{36}",job):
        raise RuntimeError("INVALID_GPU_JOB_ID")
    folder=Path("/clips")
    folder.mkdir(mode=0o700,parents=True,exist_ok=True)
    source=folder/(job+".mp4")
    alpha=folder/(job+"-alpha.webm")
    preview=folder/(job+"-preview.png")
    try:
        for p in (source,alpha,preview):p.unlink(missing_ok=True)
        with s.get(PATH+"/jobs/"+job+"/input",stream=True,timeout=(20,120)) as response:
            response.raise_for_status()
            count=0
            with source.open("xb") as out:
                os.chmod(source,0o600)
                for chunk in response.iter_content(chunk_size=262144):
                    count+=len(chunk)
                    if count>40*1024*1024:raise RuntimeError("GPU_INPUT_TOO_LARGE")
                    out.write(chunk)
        started=time.monotonic()
        result=process(source,alpha,Path("/models"))
        audit=inspect(alpha,preview)
        with alpha.open("rb") as f:
            ack=s.post(PATH+"/jobs/"+job+"/result",
                       files={"video":(job+".webm",f,"video/webm")},
                       timeout=(20,180))
        ack.raise_for_status()
        print("completed job="+job+" frames="+str(result["frames"])+
              " transparent_pct="+str(audit["transparent_pct"])+
              " seconds="+str(round(time.monotonic()-started,2)),flush=True)
    finally:
        for p in (source,alpha,preview):p.unlink(missing_ok=True)

def main():
    s=make_client()
    print("Immers GPU queue running: outbound HTTPS, server-only neuro matting",flush=True)
    while not STOP:
        try:
            response=s.get(PATH+"/next",timeout=(15,35))
            response.raise_for_status()
            payload=response.json()
            if payload.get("available"):
                handle(s,str(payload["jobId"]))
                continue
        except requests.HTTPError as exc:
            code=exc.response.status_code if exc.response is not None else "unknown"
            print("GPU queue HTTP "+str(code)+"; verify shared Railway token",flush=True)
        except (requests.RequestException,OSError,RuntimeError,MattingError,ValueError) as exc:
            print("GPU queue error "+type(exc).__name__+": "+str(exc)[:140],flush=True)
        for _ in range(10):
            if STOP:break
            time.sleep(1)

if __name__=="__main__":
    main()
