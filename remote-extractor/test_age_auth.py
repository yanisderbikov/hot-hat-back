import base64
import importlib.util
import os
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

spec = importlib.util.spec_from_file_location(
    "clip_server", Path(__file__).with_name("clip_server.py"))
clip_server = importlib.util.module_from_spec(spec)
spec.loader.exec_module(clip_server)

class AgeVerifiedSessionTests(unittest.TestCase):
    def test_missing_cookie_secret_keeps_anonymous_path(self):
        with patch.dict(os.environ, {}, clear=True), tempfile.TemporaryDirectory() as d:
            self.assertIsNone(clip_server._authorized_cookie_file(d))

    def test_filters_foreign_domains_and_sets_strict_private_permissions(self):
        txt = ("# Netscape HTTP Cookie File\n"
               ".youtube.com\tTRUE\t/\tTRUE\t2100000000\tSAPISID\tfake-test-value\n"
               "example.com\tFALSE\t/\tFALSE\t2100000000\tSID\tignore-this\n"
               ".google.com\tTRUE\t/\tTRUE\t2100000000\tSID\tfake-test-value2\n")
        with patch.dict(os.environ, {
                clip_server.AUTH_COOKIES_ENV: base64.b64encode(txt.encode()).decode()
            }), tempfile.TemporaryDirectory() as d:
            f = clip_server._authorized_cookie_file(d)
            self.assertIsNotNone(f)
            result = f.read_text()
            self.assertIn(".youtube.com",result)
            self.assertIn(".google.com",result)
            self.assertNotIn("example.com",result)
            self.assertEqual(f.stat().st_mode & 0o777, 0o600)

    def test_rejects_invalid_base64_or_non_netscape_file(self):
        for value in ("%%%not-base64", base64.b64encode(b"no header\n").decode()):
            with self.subTest(value=value), patch.dict(os.environ, {
                clip_server.AUTH_COOKIES_ENV:value
            }), tempfile.TemporaryDirectory() as d:
                self.assertIsNone(clip_server._authorized_cookie_file(d))
                self.assertEqual(list(Path(d).glob("*")), [])

    def test_age_gate_recognition(self):
        self.assertTrue(clip_server._is_age_gate("Sign in to confirm your age"))
        self.assertTrue(clip_server._is_age_gate("ERROR: This video is age-restricted"))
        self.assertFalse(clip_server._is_age_gate("Sign in to confirm you're not a bot"))

    def test_bot_sign_in_challenge_is_separate_from_age_gate(self):
        self.assertTrue(clip_server._is_bot_gate(
            "Sign in to confirm you're not a bot. This helps protect our community."))
        self.assertTrue(clip_server._is_bot_gate(
            "Sign in to confirm you’re not a bot"))
        self.assertFalse(clip_server._is_bot_gate("Sign in to confirm your age"))
        self.assertFalse(clip_server._is_bot_gate("Could not connect to YouTube"))

    def test_bot_challenge_retries_once_with_private_session_and_no_public_cookie_leak(self):
        from unittest.mock import Mock
        from subprocess import CompletedProcess
        from pathlib import Path
        video_id = "hAmi3ddwJIg"
        public_message = "ERROR: Sign in to confirm you're not a bot"
        cookie_text = ("# Netscape HTTP Cookie File\\n"
                       ".youtube.com\\tTRUE\\t/\\tTRUE\\t2100000000\\tSAPISID\\tsecret-test-token\\n")
        calls = []
        def fake_run(args, **kwargs):
            calls.append(args)
            if len(calls) == 1:
                self.assertNotIn("--cookies",args)
                return CompletedProcess(args,1,public_message)
            self.assertIn("--cookies",args)
            self.assertIn("secret-test-token",Path(args[args.index("--cookies")+1]).read_text())
            output = Path(args[args.index("--output")+1].replace("%(ext)s","mp4"))
            output.write_bytes(b"fakemp4" * 500)
            return CompletedProcess(args,0,"Video processed")
        with patch.dict(os.environ, {
            clip_server.AUTH_COOKIES_ENV:base64.b64encode(cookie_text.encode()).decode()
            }), patch.object(clip_server.subprocess,"run",side_effect=fake_run):
            data=clip_server.prepare_uncached(video_id,0)
        self.assertGreater(len(data),2048)
        self.assertEqual(len(calls),2)
        self.assertIn("--cookies",calls[1])
        self.assertNotIn("--cookies",calls[0])
        self.assertFalse(Path(calls[1][calls[1].index("--cookies")+1]).exists(),
                         "temporary authenticated cookie file is always deleted")

    def test_bot_challenge_without_auth_does_not_retry(self):
        from subprocess import CompletedProcess
        calls=[]
        def fake_run(args,**kwargs):
            calls.append(args)
            return CompletedProcess(args,1,"Sign in to confirm you're not a bot")
        with patch.dict(os.environ,{},clear=True), patch.object(
            clip_server.subprocess,"run",side_effect=fake_run):
            with self.assertRaisesRegex(RuntimeError,"YOUTUBE_BOT_CONFIRMATION_REQUIRED"):
                clip_server.prepare_uncached("hAmi3ddwJIg",0)
        self.assertEqual(len(calls),1)

if __name__ == "__main__":
    unittest.main()
