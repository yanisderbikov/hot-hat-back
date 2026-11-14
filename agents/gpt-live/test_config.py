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
        self.assertIn("_voice_confirmation(", source)
        self.assertIn("давай след слово", source)
        self.assertIn("set_attributes", source)
        self.assertIn("last_skipped_input_version", source)
        self.assertIn("skip_once", source)
        self.assertIn('if result.get("matched"):', source)
        self.assertIn("Произнеси ровно одно короткое", source)
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

    def test_duet_voice_watchdog_is_active_only_on_live_gameplay(self):
        source=Path(__file__).with_name("agent.py").read_text(encoding="utf-8")
        self.assertIn("from duet_liveness import DuetLiveness", source)
        self.assertIn('session.on("agent_state_changed", duet_agent_speech)', source)
        self.assertIn('session.on("user_state_changed", duet_partner_speech)', source)
        self.assertIn('duet_live.card_changed(', source)
        self.assertIn("action=duet_live.decide(", source)
        self.assertIn('error_code="DUET_DEADLOCK"', source)
        self.assertIn('"give_hint":', source)
        self.assertIn('"make_guess":', source)
        self.assertIn('"request_hint":', source)
        self.assertIn("duet_watchdog_task=asyncio.create_task(duet_liveness_watchdog())", source)
        self.assertIn("duet_first_reply_task, duet_watchdog_task", source)
        self.assertIn("duet_role=duo_role", source)
        self.assertIn('if self.duet_role == "guesser":', source)
        self.assertIn("Сервер УЖЕ назначил тебя", source)

    def test_short_voice_confirmation_only_from_explainer(self):
        from agent import _voice_confirmation
        for role in ("guesser", "unselected", "smalltalk", ""):
            self.assertIsNone(_voice_confirmation(role, True, 0))
        self.assertIsNone(_voice_confirmation("explainer", False, 0))
        self.assertIsNone(_voice_confirmation("explainer", 1, 0))
        variants = [_voice_confirmation("explainer", True, n) for n in range(5)]
        self.assertEqual(len(set(variants)), 5)
        self.assertTrue(all(v.endswith("!") and len(v.split()) == 1
                            for v in variants))
        self.assertEqual(_voice_confirmation("explainer", True, 5), variants[0])

    def test_duet_confirms_once_without_replaying_history_or_switching_roles(self):
        source=Path(__file__).with_name("agent.py").read_text(encoding="utf-8")
        ast.parse(source)
        self.assertIn('if result.get("skipped") and not is_duo:', source)
        self.assertIn("agent.suppress_next_word_commentary = True", source)
        self.assertIn("_voice_confirmation(", source)
        self.assertIn("if next_word:", source)
        self.assertIn("duet_guess_verification_pending", source)
        # History is memory only. In particular the GUESSER never receives
        # a second voice prompt after the EXPLAINER scores a correct guess.
        self.assertNotIn("if not is_duo and _is_voice_confirmed_guess(row):", source)
        self.assertNotIn("session.generate_reply(instructions=(\n"
                         "                                        \"Это твоя распознанная", source)
        self.assertIn("Твоя роль в ИИ-дуэте", source)
        self.assertIn("РОЛИ НЕ ОБСУЖДАЮТСЯ", source)
        self.assertIn("Не начинай с", source)
        self.assertIn("duet_wrong_guesses in (2, 4)", source)
        self.assertIn("НОВАЯ КАРТОЧКА", source)

    def test_duet_backend_prompts_speak_guesses_without_tools(self):
        from config import DUET_VOICE_INSTRUCTIONS, DUET_BACKEND_INSTRUCTIONS
        self.assertIn("НЕ требуется никакой игровой инструмент", DUET_VOICE_INSTRUCTIONS)
        self.assertIn("NO tool to submit a spoken guess", DUET_BACKEND_INSTRUCTIONS)
        self.assertIn("Never invent points", DUET_BACKEND_INSTRUCTIONS)
        self.assertIn("Only the EXPLAINER may utter", DUET_BACKEND_INSTRUCTIONS)
        self.assertIn("the GUESSER never acknowledges", DUET_BACKEND_INSTRUCTIONS)
        source=Path(__file__).with_name("agent.py").read_text(encoding="utf-8")
        self.assertIn("DUET_VOICE_INSTRUCTIONS if duet_role", source)
        self.assertIn("DUET_BACKEND_INSTRUCTIONS if is_duo", source)
        self.assertIn("else BACKEND_INSTRUCTIONS", source)

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
