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

if __name__ == "__main__":
    unittest.main()
