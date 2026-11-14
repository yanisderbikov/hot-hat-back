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
        self.assertIn("_is_voice_confirmed_guess(row)", source)
        self.assertIn("давай след слово", source)
        self.assertIn("set_attributes", source)
        self.assertIn("last_skipped_input_version", source)
        self.assertIn("skip_once", source)
        self.assertIn('if result.get("matched"):', source)
        self.assertIn("Максимум ОДНО слово", source)
        self.assertIn("suppress_next_word_commentary", source)
        client = Path(__file__).with_name("game_client.py").read_text(encoding="utf-8")
        self.assertIn('return {**result, "actionId": action_id}', client)

    def test_manual_guessed_button_never_queues_voice_congratulations(self):
        from agent import _is_voice_confirmed_guess
        self.assertFalse(_is_voice_confirmed_guess({
            "outcome": "guessed", "actionId": "guess_c29ac818"
        }))
        self.assertFalse(_is_voice_confirmed_guess({
            "outcome": "guessed", "actionId": "test-guess-4132"
        }))
        self.assertFalse(_is_voice_confirmed_guess({
            "outcome": "skipped", "actionId": "aiw_121212"
        }))
        self.assertTrue(_is_voice_confirmed_guess({
            "outcome": "guessed", "actionId": "aiw_323232"
        }))
        source=Path(__file__).with_name("agent.py").read_text(encoding="utf-8")
        self.assertIn("row.get(\"outcome\") == \"guessed\"", source)
        self.assertIn("Если человек НАЖАЛ кнопку", source)

    def test_owner_ai_duet_accepts_agent_audio_and_starts_only_one_speaker(self):
        source=Path(__file__).with_name("agent.py").read_text(encoding="utf-8")
        self.assertIn("rtc.ParticipantKind.PARTICIPANT_KIND_AGENT", source)
        self.assertIn("participant_kinds=[rtc.ParticipantKind.PARTICIPANT_KIND_AGENT]", source)
        self.assertIn("if bot_uid == designated_explainer_uid:", source)
        self.assertIn("Не говори первым:", source)
        self.assertIn("check_opening_reply", source)
        self.assertIn("one recovery", source)
        self.assertIn("await first_handle", source)
        self.assertIn("retry.exception()", source)
        self.assertGreater(source.index("watcher = asyncio.create_task(follow_game_state())"),
                           source.index("first_handle=session.generate_reply("))
        self.assertIn("(is_duo or scripted_partner)", source)
        self.assertIn('error_code="DUET_OPENING_SILENT"', source)
        self.assertIn("stop.set()", source)

    def test_scripted_partner_is_test_only_output_speech_without_human_microphone(self):
        source = Path(__file__).with_name("agent.py").read_text(encoding="utf-8")
        self.assertIn('scripted_partner = initial.get("scriptedPartner") is True', source)
        self.assertIn("audio_input=False", source)
        self.assertIn("text_input=False", source)
        self.assertIn("close_on_disconnect=False", source)
        self.assertIn("elif scripted_partner:", source)
        self.assertIn("await game.choose_explainer()", source)
        self.assertIn("симуляцию", source)

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
