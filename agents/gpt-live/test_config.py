"""Offline zero-cost checks: python -m unittest test_config.py"""
import ast
from pathlib import Path
import unittest

from config import AGENT_NAME, BACKEND_MODEL, MODEL_ID, read_settings


class SettingsTests(unittest.TestCase):
    def test_disabled_by_default(self):
        settings = read_settings({})
        self.assertFalse(settings.enabled)
        self.assertEqual(settings.max_session_seconds, 600)
        self.assertEqual(settings.voice, "marin")

    def test_explicit_enable_only(self):
        self.assertTrue(read_settings({"HOT_HAT_GPT_LIVE_ENABLED": "true"}).enabled)
        self.assertFalse(read_settings({"HOT_HAT_GPT_LIVE_ENABLED": "1"}).enabled)

    def test_bounded_session(self):
        name = "HOT_HAT_GPT_LIVE_MAX_SECONDS"
        self.assertEqual(read_settings({name: "999999"}).max_session_seconds, 600)
        self.assertEqual(read_settings({name: "-5"}).max_session_seconds, 30)
        self.assertEqual(read_settings({name: "bad"}).max_session_seconds, 600)

    def test_character_name_and_once_per_game_greeting(self):
        from config import VOICE_INSTRUCTIONS
        self.assertIn("ИИ-персонаж", VOICE_INSTRUCTIONS)
        source = Path(__file__).with_name("agent.py").read_text(encoding="utf-8")
        self.assertIn('should_greet = metadata.get("shouldGreet") is True', source)
        self.assertIn('mode == "smalltalk"', source)
        self.assertIn("Не здоровайся повторно", source)
        self.assertIn("character_name", source)
        self.assertIn("personality", source)
        self.assertIn("voice=voice", source)
        self.assertIn("is_duo", source)
        self.assertIn("participant_identity=human_uid", source)
        self.assertIn("await req.accept(", source)

    def test_confirmed_word_memory_and_acknowledgements(self):
        source=Path(__file__).with_name("agent.py").read_text(encoding="utf-8")
        self.assertIn("_history_prompt(recent_words)", source)
        self.assertIn('state.get("recentWords")', source)
        self.assertIn('row.get("outcome") == "guessed"', source)
        self.assertIn("Сервер уже ЗАСЧИТАЛ", source)
        self.assertIn("давай след слово", source)
        self.assertIn("set_attributes", source)
        self.assertIn("last_skipped_input_version", source)
        self.assertIn("skip_once", source)
        self.assertIn('if result.get("matched"):', source)
        self.assertIn("Сервер только что ЗАСЧИТАЛ", source)
        client = Path(__file__).with_name("game_client.py").read_text(encoding="utf-8")
        self.assertIn('return {**result, "actionId": action_id}', client)

    def test_correct_model_and_named_dispatch(self):
        self.assertEqual(MODEL_ID, "openai/gpt-live-1")
        self.assertEqual(AGENT_NAME, "hot-hat-gpt-live")
        self.assertTrue(BACKEND_MODEL)

    def test_worker_has_valid_python_and_no_automatic_dispatch(self):
        source = Path(__file__).with_name("agent.py").read_text(encoding="utf-8")
        ast.parse(source)
        self.assertIn("rtc_session(agent_name=AGENT_NAME, on_request=accept_hot_hat_character)", source)
        self.assertIn("if not settings.enabled:", source)
        self.assertIn("await session.aclose()", source)
        self.assertNotIn("delete_room()", source)


if __name__ == "__main__":
    unittest.main()
