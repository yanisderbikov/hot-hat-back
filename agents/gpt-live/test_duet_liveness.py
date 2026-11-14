"""Zero-cost deterministic tests for a full 60-second owner AI duet."""
import unittest
from duet_liveness import DuetLiveness


def next_action(live, now, *, role="explainer", word="Секрет",
                phase="active", paused=False, deadline_ms=70_000,
                epoch_ms=None, agent_state="listening", user_state="listening"):
    return live.decide(
        now=now, epoch_ms=int(now*1000) if epoch_ms is None else epoch_ms,
        phase=phase, role=role, word=word, paused=paused,
        deadline_ms=deadline_ms, agent_state=agent_state,
        user_state=user_state
    )


class DuetLivenessTests(unittest.TestCase):
    def test_explainer_restarts_hint_after_five_and_half_seconds_of_silence(self):
        live=DuetLiveness(last_activity=0)
        live.card_changed("turn-1","explainer","Метеорит",0)
        self.assertIsNone(next_action(live,5.1,word="Метеорит"))
        self.assertEqual(next_action(live,5.6,word="Метеорит"),"give_hint")
        self.assertIsNone(next_action(live,8,word="Метеорит"))
        live.pending=False
        self.assertIsNone(next_action(live,12.8,word="Метеорит"))
        self.assertEqual(next_action(live,13.4,word="Метеорит"),"give_hint")

    def test_explainer_moves_faster_after_an_incorrect_guess_without_interrupting(self):
        live=DuetLiveness(last_activity=0)
        live.card_changed("turn-1","explainer","Парус",0)
        live.mark_spoke(1.0)
        live.mark_heard(4.0)
        self.assertIsNone(next_action(live,7.8,word="Парус"))
        self.assertIsNone(next_action(live,8.2,word="Парус",
                                      user_state="speaking"))
        self.assertEqual(next_action(live,8.2,word="Парус"),"give_hint")
        live.pending=False
        live.mark_spoke(9.0)
        # Once the explainer starts speaking, the last peer guess no longer
        # controls prompt pacing. No redundant prompt on the new hint.
        self.assertIsNone(next_action(live,13.5,word="Парус"))
        self.assertEqual(next_action(live,16.0,word="Парус"),"give_hint")

    def test_guesser_first_requests_hint_then_guesses_only_after_hearing_peer(self):
        live=DuetLiveness(last_activity=0)
        live.card_changed("turn-1","guesser",None,0)
        self.assertIsNone(next_action(live,7.2,role="guesser",word=None))
        self.assertEqual(next_action(live,7.8,role="guesser",word=None),"request_hint")
        live.pending=False
        live.mark_heard(14)
        self.assertIsNone(next_action(live,21,role="guesser",word=None))
        self.assertEqual(next_action(live,22.2,role="guesser",word=None),"make_guess")
        self.assertEqual(live.prompts_without_voice,1)

    def test_real_speech_resets_recovery_budget_even_during_long_turn(self):
        live=DuetLiveness(last_activity=0)
        live.card_changed("turn-1","explainer","Трамвай",0)
        self.assertEqual(next_action(live,7),"give_hint")
        live.pending=False
        live.mark_spoke(10)
        self.assertEqual(live.prompts_without_voice,0)
        self.assertEqual(next_action(live,17.8),"give_hint")

    def test_thinking_speaking_and_partner_speech_are_not_interrupted(self):
        live=DuetLiveness(last_activity=0)
        live.card_changed("turn-1","explainer","Самолёт",0)
        self.assertIsNone(next_action(live,12,agent_state="speaking"))
        self.assertIsNone(next_action(live,11.8,agent_state="thinking"))
        self.assertEqual(next_action(live,12.5,agent_state="thinking"),"reset_give_hint")
        live.pending=False
        self.assertIsNone(next_action(live,12.8,user_state="speaking"))
        live.mark_heard(13)
        self.assertIsNone(next_action(live,18))

    def test_guesser_recovers_from_stuck_thinking_without_guessing_secret(self):
        live=DuetLiveness(last_activity=0)
        live.card_changed("turn-1","guesser",None,0)
        self.assertIsNone(next_action(live,11.8,role="guesser",word=None,
                                      agent_state="thinking"))
        self.assertEqual(next_action(live,12.3,role="guesser",word=None,
                                     agent_state="thinking"),"reset_request_hint")
        live.pending=False
        live.mark_heard(16.0)
        self.assertEqual(next_action(live,28.2,role="guesser",word=None,
                                     agent_state="thinking"),"reset_make_guess")

    def test_liveness_stops_before_turn_ends_or_on_pause(self):
        live=DuetLiveness(last_activity=0)
        live.card_changed("turn-1","explainer","Компас",0)
        self.assertIsNone(next_action(live,22,phase="turn_intro"))
        self.assertIsNone(next_action(live,22,paused=True))
        self.assertIsNone(next_action(live,22,deadline_ms=26_000))
        self.assertEqual(next_action(live,22,deadline_ms=60_000),"give_hint")

    def test_at_most_three_unanswered_prompts_before_paid_session_abort(self):
        live=DuetLiveness(last_activity=0)
        live.card_changed("turn-1","explainer","Солнце",0)
        for when in (7.0,15.0,23.0):
            self.assertEqual(next_action(live,when),"give_hint")
            live.pending=False
        self.assertEqual(next_action(live,31.0),"failed")
        self.assertEqual(live.prompts_without_voice,3)

    def test_next_word_rearms_recovery_without_leaking_secret_to_guesser(self):
        live=DuetLiveness(last_activity=0)
        live.card_changed("turn-1","explainer","Солнце",0)
        self.assertEqual(next_action(live,7),"give_hint")
        live.card_changed("turn-1","explainer","Луна",10)
        self.assertEqual(live.prompts_without_voice,0)
        self.assertIsNone(next_action(live,11,word="Луна"))
        self.assertEqual(next_action(live,17,word="Луна"),"give_hint")
        guess=DuetLiveness(last_activity=0)
        guess.card_changed("turn-1","guesser",None,0)
        self.assertEqual(guess.card_key,("turn-1","guesser",None))


if __name__=="__main__":
    unittest.main()
