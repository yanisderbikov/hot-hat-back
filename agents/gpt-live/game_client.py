"""Private, per-session HOT-HAT game bridge. No human JWT/token or audio persists."""
from __future__ import annotations

import asyncio
import os
import urllib.parse
import uuid

import aiohttp


class GameClient:
    def __init__(self, session_id: str):
        self.session_id = session_id
        self.base = os.environ.get("HOT_HAT_API_BASE", "").rstrip("/")
        self.secret = os.environ.get("HOT_HAT_GPT_LIVE_WORKER_SECRET", "")
        if not self.base.startswith(("https://", "http://")) or len(self.secret) < 32:
            raise RuntimeError("GPT-Live backend address/worker secret are not configured")
        self.http: aiohttp.ClientSession | None = None

    async def __aenter__(self):
        self.http = aiohttp.ClientSession(timeout=aiohttp.ClientTimeout(total=7))
        return self

    async def __aexit__(self, *_):
        if self.http is not None:
            await self.http.close()

    async def request(self, method: str, route: str, data: dict | None = None) -> dict:
        assert self.http is not None
        uri = f"{self.base}/api/v2/machine/gpt-live/{urllib.parse.quote(self.session_id)}/{route}"
        headers = {"X-HH-AI-WORKER-KEY": self.secret}
        async with self.http.request(method, uri, json=data, headers=headers) as resp:
            if resp.status >= 400:
                raise RuntimeError(f"AI_GAME_HTTP_{resp.status}_{route}")
            return await resp.json()

    async def context(self) -> dict:
        return await self.request("GET", "context")

    async def choose_explainer(self) -> dict:
        return await self.request("POST", "choose-explainer", {})

    async def heard(self, human_uid: str, transcript: str) -> dict:
        return await self.request("POST", "heard", {
            "speakerUid": human_uid, "transcript": transcript[:500],
            "actionId": "aiw_" + uuid.uuid4().hex,
        })

    async def skip(self, human_uid: str, transcript: str) -> dict:
        return await self.request("POST", "skip", {
            "speakerUid": human_uid, "transcript": transcript[:500],
            "actionId": "ais_" + uuid.uuid4().hex,
        })

    async def report(self, voice_seconds: float, input_tokens: int = 0,
                     output_tokens: int = 0, finished: bool = False,
                     error_code: str = "") -> dict:
        return await self.request("POST", "usage", {
            "voiceSeconds": max(0, min(180, float(voice_seconds))),
            "backendInputTokens": max(0, int(input_tokens)),
            "backendOutputTokens": max(0, int(output_tokens)),
            "finished": bool(finished), "errorCode": error_code[:120],
        })
