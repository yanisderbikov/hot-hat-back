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
        self.assertIn("await stop_stale_explanation()", source)
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
        # Server preassigns the role. Native commentary primes the FIRST
        # speech while the watcher is still starting; there is no competing
        # generate_reply or artificial ten-second retry race.
        self.assertIn("agent.prime_first_explainer_hint(current)", source)
        self.assertIn("Duet first clue primed", source)
        self.assertNotIn("first_handle=session.generate_reply(", source)
        self.assertIn("(is_duo or scripted_partner)", source)
        self.assertIn('error_code="DUET_OPENING_FAILED"', source)
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
        self.assertIn('if result.get("skipped"):', source)
        self.assertIn("agent.suppress_next_word_commentary = False", source)
        self.assertIn("agent.pending_voice_confirmation", source)
        self.assertIn("_voice_confirmation(", source)
        self.assertIn("card_prompts.schedule(",source)
        self.assertIn("after_verified_score=changed_active_card",source)
        self.assertNotIn("if next_word:",source)
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
        self.assertNotIn('"НОВАЯ КАРТОЧКА: объясни текущее слово"', source)

    def test_verified_guess_must_not_cancel_already_started_next_card(self):
        source=Path(__file__).with_name("agent.py").read_text(encoding="utf-8")
        ast.parse(source)
        self.assertIn('if active_prompt_key == candidate_card_key:',source)
        self.assertIn('Scored old card already advanced',source)
        start=source.index('if result.get("matched"):',source.index('async def on_heard(event):'))
        end=source.index('elif result.get("reason") == "FORM_NOT_EXACT" and is_duo:',start)
        scored=source[start:end]
        self.assertIn('if active_prompt_key == candidate_card_key:',scored)
        self.assertIn('await card_prompts.close()',scored)
        self.assertIn('else:\n                            logger.info(',scored)
        self.assertNotIn('active_prompt_key = None\n                        await card_prompts.close()',scored)

    def test_fast_adaptive_feedback_on_partial_and_interrupted_guesses(self):
        source=Path(__file__).with_name("agent.py").read_text(encoding="utf-8")
        ast.parse(source)
        self.assertIn("from guess_guidance import (",source)
        self.assertIn("guess_signature, partial_compound_guess,",source)
        # Server scoring remains separate; only a finalized, authenticated
        # peer transcript can invoke the prompt coordinator.
        self.assertIn('if not getattr(event, "is_final", False):',source)
        self.assertIn('result = await game.heard(human_uid, phrase)',source)
        self.assertIn('partial_compound_guess(candidate_card_key[2], phrase)',source)
        self.assertIn('candidate_card_key==active_prompt_key',source)
        self.assertIn('current.get("cardRevision")',source)
        self.assertIn('already_spoken_component_hint(',source)
        self.assertIn('partial.instruction(',source)
        self.assertIn('card_prompts.react_to_guess(',source)
        self.assertIn('DUET_VOICE_FEEDBACK_TOPIC',source)
        self.assertIn('verified_duet_feedback_packet(',source)
        self.assertIn('duet_utterance_kind(phrase)',source)
        self.assertIn('if is_duo and utterance_kind != "guess":',source)
        self.assertIn('ctx.room.on("data_received",on_duet_feedback_packet)',source)
        self.assertNotIn('card_prompts.schedule(candidate_card_key, followup)',source)
        # In human+AI the same clarification is active, not only in AI+AI.
        self.assertIn('and solo_explainer:',source)
        self.assertIn('partial_compound_guess(\n',source)
        # No watchdog direct generate_reply races with new-card speech.
        self.assertIn('if base_action == "give_hint":',source)
        self.assertIn('card_prompts.schedule(active_prompt_key,phrase)',source)
        self.assertIn('duet_guesser_repeat_count in (2, 4, 7)',source)

    def test_duet_reliable_own_voice_relay_is_tied_to_authenticated_peer(self):
        source=Path(__file__).with_name("agent.py").read_text(encoding="utf-8")
        ast.parse(source)
        self.assertIn('ctx.room.on("data_received",on_duet_guess_packet)',source)
        self.assertIn("verified_duet_voice_packet(",source)
        self.assertIn('sender=sender,expected_peer=human_uid,',source)
        self.assertIn('revision=int(active_prompt_key[3] or 0)',source)
        self.assertIn('destination_identities=[human_uid]',source)
        self.assertIn("topic=DUET_VOICE_GUESS_TOPIC",source)
        self.assertIn('result.get("reason") == "FORM_NOT_EXACT"',source)
        self.assertIn('SimpleNamespace(is_final=True,transcript=candidate)',source)
        self.assertIn('if received_at-duet_recent_guess_keys.get(dedup_key,-100.0)<1.6:',source)
        # The relay never transmits a secret; only what the guesser said.
        begin=source.index('await ctx.room.local_participant.publish_data(')
        end=source.index('logger.info("Duet own-voice relay sent',begin)
        self.assertNotIn('currentWord',source[begin:end])
        self.assertNotIn('secret',source[begin:end])

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

    def test_solo_explainer_liveness_and_duet_own_speech_scoring(self):
        source=Path(__file__).with_name("agent.py").read_text(encoding="utf-8")
        client=Path(__file__).with_name("game_client.py").read_text(encoding="utf-8")
        ast.parse(source)
        self.assertIn("solo_explainer =",source)
        self.assertIn("max_prompts_without_voice=3 if is_duo else 8",source)
        self.assertIn("if watch_game_speech:",source)
        self.assertIn('solo_explainer and duet_snapshot.get("role") != "explainer"',source)
        self.assertIn('session.on("conversation_item_added"',source)
        self.assertIn("on_guesser_own_voice",source)
        self.assertIn("game.duet_guesser_spoke(phrase)",source)
        self.assertIn("pending_duet_ack",source)
        # A single AI explaining for a person must not be left silent after
        # the short server-confirmed "Верно!" acknowledgement.
        self.assertIn("await stop_stale_explanation()",source)
        self.assertIn("card_prompts.schedule(",source)
        self.assertIn("after_verified_score=changed_active_card",source)
        self.assertIn("The server already advanced the card",source)
        self.assertIn('str(scored.get("actionId") or "").startswith("aig_")',source)
        self.assertIn("async def duet_guesser_spoke(",client)
        self.assertIn('"duet-guesser-spoke"',client)

    def test_wrong_guess_never_force_interrupts_after_server_checks_same_card(self):
        source=Path(__file__).with_name("agent.py").read_text(encoding="utf-8")
        ast.parse(source)
        start=source.index('elif result.get("reason") == "FORM_NOT_EXACT" and is_duo:')
        end=source.index('except Exception as exc:',start)
        rejection=source[start:end]
        self.assertIn('candidate_card_key==active_prompt_key',rejection)
        self.assertIn('now-last_card_switched_at>=0.9',rejection)
        self.assertIn('current.get("cardRevision")',rejection)
        self.assertIn('if still_same:',rejection)
        self.assertNotIn('await stop_stale_explanation()',rejection)
        self.assertIn('card_prompts.schedule(candidate_card_key,',rejection)
        self.assertNotIn("Ты назвал неверно",rejection)

    def test_two_ai_never_force_interrupts_on_vad_only_on_final_transcript(self):
        source=Path(__file__).with_name("agent.py").read_text(encoding="utf-8")
        start=source.index("        def duet_partner_speech(event):")
        end=source.index("        async def on_heard(event):",start)
        self.assertNotIn("stop_stale_explanation()",source[start:end])
        start=source.index("        async def on_heard(event):")
        end=source.index("        session.on(\"user_input_transcribed\"",start)
        snippet=source[start:end]
        self.assertIn('if not getattr(event, "is_final", False):',snippet)
        self.assertIn('if still_same:',snippet)

    def test_every_gpt_live_role_has_preloaded_vad_for_fast_barge_in(self):
        source=Path(__file__).with_name("agent.py").read_text(encoding="utf-8")
        docker=Path(__file__).with_name("Dockerfile").read_text(encoding="utf-8")
        requirements=Path(__file__).with_name("requirements.txt").read_text(
            encoding="utf-8")
        ast.parse(source)
        self.assertIn("from livekit.plugins import silero",source)
        self.assertIn("server.setup_fnc = _prewarm_vad",source)
        self.assertIn('vad=ctx.proc.userdata["vad"]',source)
        self.assertIn("min_speech_duration=0.12",source)
        self.assertIn("min_silence_duration=0.35",source)
        self.assertIn("livekit-agents[silero]",requirements)
        self.assertIn("RUN python agent.py download-files",docker)

    def test_human_role_selection_primes_first_card_without_competing_reply(self):
        source=Path(__file__).with_name("agent.py").read_text(encoding="utf-8")
        ast.parse(source)
        self.assertIn("def prime_first_explainer_hint(self, state: dict)",source)
        self.assertIn("self.duplex_session.append_thinking(",source)
        self.assertIn("self.duplex_session.append_commentary(",source)
        self.assertIn("self.suppress_next_word_commentary=True",source)
        self.assertIn("agent.prime_first_explainer_hint(after)",source)
        tool=source[source.index("async def choose_to_explain("):
                    source.index("async def skip_once(",source.index("async def choose_to_explain("))]
        self.assertNotIn("generate_reply(",tool)

    def test_no_spoken_timer_or_deadline_coaching_in_game_prompt_paths(self):
        source=Path(__file__).with_name("agent.py").read_text(encoding="utf-8")
        prompts=Path(__file__).with_name("card_prompts.py").read_text(
            encoding="utf-8")
        from config import VOICE_INSTRUCTIONS, DUET_VOICE_INSTRUCTIONS
        ast.parse(source)
        self.assertNotIn("Время уже идёт",source)
        self.assertNotIn("часы идут",source)
        self.assertNotIn("Время игры идёт!",prompts)
        self.assertNotIn("Продолжай СЕЙЧАС короткую",prompts)
        # Explicitly forbid audible timing/process chatter in both modes.
        self.assertIn("время капает",VOICE_INSTRUCTIONS)
        self.assertIn("Никогда не произноси фразы о времени",
                      DUET_VOICE_INSTRUCTIONS)

    def test_AI_spoken_yes_is_a_server_scoring_action_not_a_sound_effect(self):
        from agent import _standalone_voice_approval
        for text in ("Да!", "Верно.", "Точно!", "Правильно", "Это оно",
                     "Угадал!", "Так и есть", "Да! Ещё один признак.",
                     "Верно. Следующая загадка.", "Верно, верно!", 
                     "Да, да!", "Точно, правильно!"):
            self.assertIsNotNone(_standalone_voice_approval(text),text)
        for text in ("да, но какой?", "да, близко", "да нет",
                     "давай дальше", "да это предмет?", "да! но не такой",
                     "Да! Почти, но другой.", "Верно! Но нет, другой.",
                     "Да, да, но какой?", "Верно, почти",
                     "возможно", ""):
            self.assertIsNone(_standalone_voice_approval(text),text)
        source=Path(__file__).with_name("agent.py").read_text(encoding="utf-8")
        client=Path(__file__).with_name("game_client.py").read_text(
            encoding="utf-8")
        self.assertIn('async def on_assistant_spoken_approval(event):',source)
        self.assertIn('async def confirm_correct_human_guess(',source)
        self.assertIn('async def commit_solo_approval(',source)
        self.assertIn('agent.confirm_last_human_guess = commit_solo_approval',source)
        self.assertIn('last_peer_speech_started_at = time.monotonic()',source)
        self.assertIn('deadline = time.monotonic() + 0.9',source)
        self.assertIn('if source == "tool":',source)
        self.assertIn('guess.get("approval_requested")',source)
        self.assertIn('time.monotonic() - last_tool_score_at < 1.5',source)
        self.assertIn('await game.approve_human_guess(',source)
        self.assertIn('if not result.get("matched"):',source)
        self.assertIn('pending_human_guess["key"] != key',source)
        self.assertIn('"approve-human-guess"',client)
        self.assertIn('async with score_lock:',source)

    def test_score_wakes_next_card_without_forcing_idle_duplex_reset(self):
        source=Path(__file__).with_name("agent.py").read_text(encoding="utf-8")
        ast.parse(source)
        # Never interrupt on unverified speech. Stop only stale local
        # playback when the authoritative server has counted a word.
        self.assertIn('async def stop_stale_explanation(',source)
        self.assertIn('not verified_score or',source)
        self.assertIn('session.interrupt(force=False)',source)
        self.assertNotIn('session.interrupt(force=True)',source)
        self.assertIn('verified_score=changed_active_card or beginning_explanation',source)
        self.assertIn('attempts=2,',source)
        self.assertIn('response_timeout=10.5,',source)
        self.assertIn('commentary_grace=1.0 if is_duo else 1.2',source)
        self.assertIn('async def reset_orphaned_speech():',source)

    def test_fast_voice_duet_and_human_ai_guesser_regressions(self):
        source=Path(__file__).with_name("agent.py").read_text(encoding="utf-8")
        from config import DUET_BACKEND_INSTRUCTIONS, DUET_VOICE_INSTRUCTIONS
        ast.parse(source)
        self.assertIn('"allow_interruptions": True',source)
        self.assertIn('"min_interruption_duration": 0.20',source)
        self.assertIn("async def stop_stale_explanation(*, verified_score: bool = False)",source)
        self.assertIn("await stop_stale_explanation(verified_score=True)",source)
        self.assertIn('if result.get("turnClosed"):',source)
        self.assertIn('await card_prompts.close()',source)
        self.assertIn("await stop_stale_explanation()",source)
        self.assertIn('if mode == "game" and not scripted_partner:',source)
        self.assertIn('if mode != "game" or scripted_partner or not is_duo:',source)
        self.assertIn("session.generate_reply(instructions=(",source)
        self.assertIn('await asyncio.sleep(0.22 if role in ("explainer","guesser")',source)
        self.assertIn("ПЕРЕБИВ",DUET_VOICE_INSTRUCTIONS)
        self.assertIn("INTERRUPTS",DUET_BACKEND_INSTRUCTIONS)
        self.assertIn("Peer voice score: mode=",source)

    def test_human_rublisha_uses_natural_dialogue_without_technical_voice(self):
        source=Path(__file__).with_name("agent.py").read_text(encoding="utf-8")
        from config import VOICE_INSTRUCTIONS, BACKEND_INSTRUCTIONS
        ast.parse(source)
        self.assertIn("duet_turn_settings = ({",source)
        self.assertIn('} if is_duo else {})',source)
        self.assertIn("**duet_turn_settings,",source)
        self.assertIn('and is_duo:',source)
        self.assertNotIn('now-duet_live.last_activity<11.0:',source)
        self.assertIn('if card_prompts.busy:',source)
        self.assertIn("живо",VOICE_INSTRUCTIONS)
        self.assertIn("НИКОГДА не произноси",VOICE_INSTRUCTIONS)
        self.assertIn("разные подсказки",VOICE_INSTRUCTIONS)
        self.assertIn("PRIVATE implementation details",BACKEND_INSTRUCTIONS)
        # A rejection must NOT issue a forced voice reply for a HUMAN.
        beginning=source.index('elif result.get("reason") == "FORM_NOT_EXACT" and is_duo:')
        ending=source.index('            except Exception as exc:',beginning)
        rejection=source[beginning:ending]
        self.assertNotIn('Ответ НЕ засчитан сервером',rejection)
        self.assertNotIn('СЕРВЕР НЕ ЗАСЧИТАЛ',rejection)
        self.assertNotIn('session.generate_reply(',rejection)
        self.assertIn('candidate_card_key==active_prompt_key',rejection)
        self.assertIn('if still_same:',rejection)
        self.assertNotIn('await stop_stale_explanation()',rejection)
        self.assertIn('and is_duo:',rejection.splitlines()[0])

    def test_two_ai_interrupts_only_on_final_server_verified_same_card(self):
        source=Path(__file__).with_name("agent.py").read_text(encoding="utf-8")
        start=source.index("def duet_partner_speech(event):")
        end=source.index('session.on("agent_state_changed"',start)
        on_speech=source[start:end]
        self.assertNotIn("stop_stale_explanation()",on_speech)
        self.assertIn("duet_live.mark_heard",on_speech)
        start=source.index('elif result.get("reason") == "FORM_NOT_EXACT" and is_duo:')
        end=source.index('            except Exception as exc:',start)
        on_mismatch=source[start:end]
        self.assertIn("if still_same:",on_mismatch)
        self.assertNotIn("await stop_stale_explanation()",on_mismatch)
        self.assertNotIn("session.generate_reply(",on_mismatch)
        self.assertIn("duet_wrong_guesses += 1",on_mismatch)
        self.assertIn("request_duet_reply(instructions: str)",source)
        self.assertIn('peer_state == "speaking"',source)
        self.assertIn("now - last_duet_reply_requested < 1.25",source)
        self.assertIn("handle=request_duet_reply(phrase)",source)
        self.assertIn("await asyncio.wait_for(handle,timeout=9)",source)
        self.assertIn("fast_duet=is_duo",source)

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


    def test_human_only_confirmation_and_one_partner_in_public_voice_room(self):
        source=Path(__file__).with_name("agent.py").read_text(encoding="utf-8")
        from config import VOICE_INSTRUCTIONS
        ast.parse(source)
        self.assertIn('if mode == "game" and not scripted_partner:',source)
        self.assertIn('if mode != "game" or scripted_partner or not is_duo:',source)
        self.assertIn("Только ЧЕЛОВЕК-ОБЪЯСНЯЮЩИЙ",source)
        self.assertIn("ТОЛЬКО человек нажимает",VOICE_INSTRUCTIONS)
        self.assertEqual(source.count("participant_identity=human_uid,"),2)
        self.assertEqual(source.count("audio_output=True,"),3)
        self.assertEqual(source.count("audio_input=True,"),2)
        self.assertEqual(source.count("text_input=False,"),3)
        self.assertIn("participant_kinds=[rtc.ParticipantKind.PARTICIPANT_KIND_AGENT]",source)

if __name__ == "__main__":
    unittest.main()



class TurnStateRegressions(unittest.TestCase):
    def test_human_delegation_and_ai_role_agreement_trigger_authoritative_start(self):
        from agent import _human_delegated_explaining, _ai_committed_to_explaining
        for phrase in ("Ты начинаешь", "Давай ты", "Объясняй ты",
                       "Твоя очередь", "Ну давай, начинай"):
            self.assertTrue(_human_delegated_explaining(phrase),phrase)
        for phrase in ("Я объясню", "Хорошо, я начинаю",
                       "Давай я", "Я буду объяснять"):
            self.assertTrue(_ai_committed_to_explaining(phrase),phrase)
        self.assertFalse(_human_delegated_explaining("Я объясняю, ты угадываешь"))
        self.assertFalse(_ai_committed_to_explaining("Не буду объяснять"))

    def test_old_solved_secret_never_reenters_live_llm_context(self):
        from agent import _history_prompt
        ctx=_history_prompt([{"word":"Магнит","outcome":"guessed","actionId":"a1"},
                             {"word":"Бинокль","outcome":"guessed","actionId":"a2"},
                             {"word":"Кенгуру","outcome":"skipped","actionId":"a3"}])
        for secret in ("Магнит","Бинокль","Кенгуру"):
            self.assertNotIn(secret,ctx)
        self.assertIn("угадано 2",ctx)
        self.assertIn("пропущено 1",ctx)

    def test_no_unverified_intro_clues_and_card_version_switch_recovery(self):
        source=Path(__file__).with_name("agent.py").read_text(encoding="utf-8")
        ast.parse(source)
        self.assertIn('async def ensure_server_started_for_bot(',source)
        self.assertIn('await game.choose_explainer()',source)
        self.assertIn('after.get("role") != "explainer"',source)
        self.assertIn('if _human_delegated_explaining(phrase):',source)
        self.assertIn('if _ai_committed_to_explaining(',source)
        self.assertIn('session.on("conversation_item_added",',source)
        self.assertIn('int(state.get("cardRevision") or 0)',source)
        self.assertIn('previous[3] != key[3]',source)
        self.assertIn('if changed_active_card or beginning_explanation:',source)
        self.assertIn('await stop_stale_explanation()',source)
        self.assertIn('agent.suppress_next_word_commentary = False',source)


class NaturalClueContractRegressions(unittest.TestCase):
    def test_secret_is_ephemeral_private_context(self):
        source=Path(__file__).with_name("agent.py").read_text(encoding="utf-8")
        self.assertIn('agent.duplex_session.append_thinking(',source)
        self.assertNotIn('"ПРЕДЫДУЩАЯ КАРТОЧКА ЗАКРЫТА.',source)
        self.assertIn('commentary=lambda text: agent.duplex_session.append_commentary(text)',source)
        self.assertIn('require_voice=True',source)
        self.assertIn('reset=reset_orphaned_speech',source)
    def test_speech_request_has_no_meta_headings(self):
        source=Path(__file__).with_name("agent.py").read_text(encoding="utf-8")
        begin=source.index('reply_instruction=(\n')
        end=source.index('card_prompts.schedule(',begin)
        block=source[begin:end]
        self.assertNotIn('"НОВАЯ КАРТОЧКА:',block)
        self.assertNotIn('"Сервер засчитал голосовой ответ.',block)
        self.assertIn('Немедленно произнеси короткую конкретную',block)
