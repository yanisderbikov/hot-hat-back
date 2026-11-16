"""SCRUM-52: real compound-word guesses should produce useful guidance."""
import unittest

from guess_guidance import (CompoundTranscriptWindow, DUET_VOICE_GUESS_TOPIC,
                            guess_signature, partial_compound_guess,
                            verified_duet_voice_packet)


class GuessGuidanceTests(unittest.TestCase):
    def test_salty_cucumber_noun_only_is_partially_correct(self):
        result = partial_compound_guess("Солёный огурец", "Я думаю, огурец!")
        self.assertIsNotNone(result)
        self.assertEqual("missing_modifier", result.kind)
        self.assertIn("какой именно", result.instruction())
        self.assertNotIn("соленый", result.instruction().lower())

    def test_repeated_cucumber_requires_new_modifier_clue(self):
        result = partial_compound_guess("соленый огурец", "огурец?")
        self.assertIn("способ приготовления", result.instruction(repeated=True))
        self.assertNotIn("соленый", result.instruction(repeated=True))

    def test_inflected_russian_noun_is_still_a_partial_not_a_score(self):
        result = partial_compound_guess("Солёный огурец", "Огурца?")
        self.assertEqual("missing_modifier", result.kind)

    def test_adjective_without_headword_asks_for_object(self):
        result = partial_compound_guess("снежный человек", "Это снежный?")
        self.assertEqual("missing_object", result.kind)
        self.assertIn("что именно", result.instruction())

    def test_other_compounds_can_use_same_general_mechanism(self):
        result = partial_compound_guess("Снежный человек", "человек")
        self.assertEqual("missing_modifier", result.kind)

    def test_completely_incorrect_guess_does_not_claim_a_partial_match(self):
        self.assertIsNone(partial_compound_guess("соленый огурец", "помидор"))
        self.assertIsNone(partial_compound_guess("снежный человек", "море"))

    def test_exact_full_answer_stays_with_authoritative_server_scorer(self):
        for phrase in ("соленый огурец", "Это солёный огурец!",
                       "Огурец солёный?"):
            self.assertIsNone(partial_compound_guess("соленый огурец", phrase))

    def test_single_words_never_get_partial_success(self):
        self.assertIsNone(partial_compound_guess("Будильник", "буди"))
        self.assertIsNone(partial_compound_guess("Компас", "компост"))

    def test_related_noun_is_not_automatically_called_correct(self):
        self.assertIsNone(partial_compound_guess("соленый огурец", "огурчик"))
        self.assertIsNone(partial_compound_guess("соленый огурец", "огуречный"))

    def test_no_user_or_secret_text_appears_in_guidance(self):
        guessed = "Огурец, а-а, это огурец, точно?"
        result = partial_compound_guess("соленый огурец", guessed)
        self.assertIsNotNone(result)
        self.assertNotIn("огурец", result.instruction().lower())
        self.assertNotIn(guessed, result.instruction())

    def test_snowman_split_during_interruption_can_be_combined_for_scoring(self):
        window=CompoundTranscriptWindow()
        card=("turn1","explainer","снежный человек",2,False)
        self.assertIsNone(window.offer(secret="снежный человек",card_key=card,
                                       phrase="Снежный?",now=10.0))
        self.assertEqual("Снежный? человек!",
                         window.offer(secret="снежный человек",card_key=card,
                                      phrase="человек!",now=11.2))

    def test_compound_buffer_never_joins_different_cards_or_old_guesses(self):
        window=CompoundTranscriptWindow()
        old=("turn","explainer","соленый огурец",1,False)
        new=("turn","explainer","снежный человек",2,False)
        window.offer(secret="соленый огурец",card_key=old,phrase="огурец",now=1)
        self.assertIsNone(window.offer(secret="снежный человек",card_key=new,
                                       phrase="снежный",now=2))
        self.assertIsNone(window.offer(secret="снежный человек",card_key=new,
                                       phrase="человек",now=8))
        window.clear()
        self.assertIsNone(window.offer(secret="снежный человек",card_key=new,
                                       phrase="человек",now=9))

    def test_not_stitching_unrelated_or_already_complete_guesses(self):
        window=CompoundTranscriptWindow()
        key=("turn","explainer","снежный человек",2,False)
        self.assertIsNone(window.offer(secret="снежный человек",card_key=key,
                                       phrase="море",now=0))
        self.assertIsNone(window.offer(secret="снежный человек",card_key=key,
                                       phrase="снежный",now=1))
        self.assertIsNone(window.offer(secret="снежный человек",card_key=key,
                                       phrase="снежный человек",now=2))

    def test_private_livekit_relay_only_accepts_this_peer_and_card(self):
        import json
        data=json.dumps({"v":1,"turnId":"round-A","cardRevision":4,
                         "phrase":"огурец?"},ensure_ascii=False).encode("utf-8")
        args=dict(topic=DUET_VOICE_GUESS_TOPIC,sender="trusted-bot",
                  expected_peer="trusted-bot",turn_id="round-A",revision=4)
        self.assertEqual("огурец?",verified_duet_voice_packet(data,**args))
        self.assertIsNone(verified_duet_voice_packet(
            data,**{**args,"sender":"another-team"}))
        self.assertIsNone(verified_duet_voice_packet(
            data,**{**args,"topic":"untrusted-topic"}))
        self.assertIsNone(verified_duet_voice_packet(
            data,**{**args,"turn_id":"old-round"}))
        self.assertIsNone(verified_duet_voice_packet(
            data,**{**args,"revision":5}))

    def test_voice_relay_rejects_non_speech_and_oversized_packets(self):
        import json
        args=dict(topic=DUET_VOICE_GUESS_TOPIC,sender="bot",
                  expected_peer="bot",turn_id="round",revision=0)
        for data in (b"",b"not-json",b"x"*801,
                     json.dumps({"v":1,"turnId":"round","cardRevision":0,
                                 "phrase":123}).encode(),
                     json.dumps({"v":1,"turnId":"round","cardRevision":0,
                                 "phrase":" "}).encode(),
                     json.dumps({"v":1,"turnId":"round","cardRevision":True,
                                 "phrase":"огурец"}).encode()):
            self.assertIsNone(verified_duet_voice_packet(data,**args))

    def test_guesser_repetition_signature_ignores_filler(self):
        self.assertEqual(guess_signature("Это огурец?"),
                         guess_signature("ну, огурец!"))
        self.assertNotEqual(guess_signature("Огурец?"),
                            guess_signature("солёный огурец!"))


if __name__ == "__main__":
    unittest.main()
