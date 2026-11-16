"""SCRUM-52: real compound-word guesses should produce useful guidance."""
import unittest

from guess_guidance import guess_signature, partial_compound_guess


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

    def test_guesser_repetition_signature_ignores_filler(self):
        self.assertEqual(guess_signature("Это огурец?"),
                         guess_signature("ну, огурец!"))
        self.assertNotEqual(guess_signature("Огурец?"),
                            guess_signature("солёный огурец!"))


if __name__ == "__main__":
    unittest.main()
