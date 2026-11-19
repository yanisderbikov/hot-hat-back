"""Deterministic, free tests for GPT-Live new-card speech coordination."""
import asyncio
import unittest

from card_prompts import CardPromptCoordinator


class CardPromptCoordinatorTests(unittest.IsolatedAsyncioTestCase):
    async def test_duet_same_card_reaction_never_starts_competing_reply(self):
        key=("turn","explainer","солёный огурец",2,False)
        generated=[]
        commentary=[]
        hold=asyncio.get_running_loop().create_future()
        coord=CardPromptCoordinator(
            generate=lambda txt:generated.append(txt) or hold,
            commentary=commentary.append,
            state=lambda:("listening","listening"),
            is_current=lambda k:k==key,
            spoke_at=lambda:0.0,
            commentary_grace=0.15,response_timeout=0.05,
            attempts=1)
        coord.schedule(key,"old hint")
        await asyncio.wait_for(coord._task,timeout=1)
        self.assertEqual(generated,["old hint"])
        self.assertTrue(coord.react_to_guess(
            key,"ask for the missing modifier"))
        self.assertEqual(generated,["old hint"])
        self.assertIn("ask for the missing modifier",commentary)
        hold.set_result(None)
        await coord.close()

    async def test_new_card_immediately_generates_speech_without_waiting_for_human(self):
        key = ("round1", "explainer", "Маяк", 3, False)
        called = []
        async def handle():
            return None
        coord = CardPromptCoordinator(generate=lambda text: called.append(text) or asyncio.create_task(handle()),
            state=lambda: ("listening", "listening"), is_current=lambda k:k==key,
            spoke_at=lambda: 0.0)
        coord.schedule(key, "Подсказка Маяк")
        await coord._task
        self.assertEqual(called, ["Подсказка Маяк"])
        await coord.close()

    async def test_refused_voice_request_retries_instead_of_losing_card(self):
        key = ("round2", "explainer", "Кедр", 4, False)
        attempts = []
        async def refused():
            raise RuntimeError("the model did not start speaking")
        async def success():
            return None
        def generate(text):
            attempts.append(text)
            return asyncio.create_task(refused() if len(attempts)==1 else success())
        coord=CardPromptCoordinator(generate=generate,
            state=lambda:("listening","listening"),is_current=lambda k:k==key,
            spoke_at=lambda:0.0,sleep=lambda _:asyncio.sleep(0))
        coord.schedule(key,"Следующая подсказка")
        await coord._task
        self.assertEqual(len(attempts),2)
        await coord.close()

    async def test_stale_card_is_cancelled_and_never_replayed(self):
        key=("r1","explainer","Магнит",1,False)
        prompts=[]
        next_key=("r1","explainer","Бинокль",2,False)
        active=[key]
        async def finish():return None
        coord=CardPromptCoordinator(
            generate=lambda s:prompts.append(s) or asyncio.create_task(finish()),
            state=lambda:("listening","listening"),is_current=lambda k:k==active[0],
            spoke_at=lambda:0.0)
        coord.schedule(key,"old")
        active[0]=next_key
        coord.schedule(next_key,"new")
        await coord._task
        self.assertEqual(prompts,["new"])
        await coord.close()

    async def test_busy_peer_does_not_trigger_a_competing_reply_request(self):
        key=("r1","explainer","Арбуз",3,False)
        called=[]
        peer=["speaking"]
        async def finish():return None
        # Wait out the real 2.5-second maximum peer-speech yield.
        # Do not monkeypatch time.monotonic: asyncio shares that clock.
        coord=CardPromptCoordinator(
            generate=lambda s:called.append(s) or asyncio.create_task(finish()),
            state=lambda:("listening",peer[0]),
            is_current=lambda k:k==key,spoke_at=lambda:0.0,
            sleep=asyncio.sleep)
        coord.schedule(key,"give next hint")
        await asyncio.wait_for(coord._task,timeout=4)
        self.assertEqual(called,[], "Never start speech while partner is speaking")
        peer[0]="listening"
        coord.schedule(key,"give next hint")
        await coord._task
        self.assertEqual(called,["give next hint"])
        await coord.close()

    async def test_final_guess_updates_same_card_without_interrupting_active_generation(self):
        key=("r1","explainer","Снежный человек",4,False)
        release=asyncio.Event()
        started=asyncio.Event()
        requested=[]
        commentary=[]
        async def live_reply():
            started.set()
            await release.wait()
        coord=CardPromptCoordinator(
            generate=lambda s:requested.append(s) or asyncio.create_task(live_reply()),
            commentary=lambda s:commentary.append(s),
            state=lambda:("listening","listening"),
            is_current=lambda k:k==key,spoke_at=lambda:0.0,
            sleep=lambda _:asyncio.sleep(0),require_voice=False)
        coord.schedule(key,"general clue")
        await asyncio.wait_for(started.wait(),timeout=2)
        coord.schedule(key,"yes, but what kind?")
        self.assertEqual(requested,["general clue"])
        self.assertEqual(commentary,["general clue","yes, but what kind?"])
        release.set()
        await coord._task
        self.assertEqual(requested,["general clue"])
        await coord.close()


    async def test_verified_duet_score_starts_new_card_even_when_old_thinking_has_no_handle(self):
        # Observed Nov 19: a successful AI+AI guess advances the card, yet
        # the interrupted old GPT-Live turn can still report "thinking".
        # No tracked generate_reply handle existed, so the new card used
        # to wait 2.5 seconds and then silently abandon the speech request.
        key=("turn","explainer","морская звезда",7,False)
        generated=[]
        commentary=[]
        coord=CardPromptCoordinator(
            generate=lambda txt:generated.append(txt) or
                asyncio.create_task(asyncio.sleep(0)),
            commentary=commentary.append,
            state=lambda:("thinking","listening"),
            is_current=lambda k:k==key,
            spoke_at=lambda:0.0,
            response_timeout=0.1,commentary_grace=0.15,attempts=1,
            fast_score_handoff=True)
        coord.schedule(key,"new clue after scored word",
                       after_verified_score=True)
        await asyncio.wait_for(coord._task,timeout=1.0)
        self.assertEqual(generated,["new clue after scored word"])
        self.assertEqual(commentary,["new clue after scored word"])
        await coord.close()

    async def test_rublisha_solo_preserves_old_verified_score_thinking_policy(self):
        # The user likes solo/Rublisha: fast_score_handoff is strictly
        # opt-in for two-AI teams. Its default must not preempt an old
        # thinking state without a tracked pending provider request.
        key=("turn","explainer","компас",7,False)
        generated=[]
        coord=CardPromptCoordinator(
            generate=lambda txt:generated.append(txt) or
                asyncio.create_task(asyncio.sleep(0)),
            state=lambda:("thinking","listening"),
            is_current=lambda k:k==key,
            spoke_at=lambda:0.0,
            attempts=1)
        coord.schedule(key,"solo next clue",after_verified_score=True)
        await asyncio.sleep(0.35)
        self.assertEqual(generated,[],
            "The fast after-score handoff must not change Rublisha")
        await coord.close()

    async def test_unverified_same_card_never_preempts_stale_thinking(self):
        key=("turn","explainer","снежный человек",7,False)
        requested=[]
        coord=CardPromptCoordinator(
            generate=lambda txt:requested.append(txt) or
                asyncio.create_task(asyncio.sleep(0)),
            state=lambda:("thinking","listening"),
            is_current=lambda k:k==key,
            spoke_at=lambda:0.0,
            attempts=1)
        coord.schedule(key,"a same-card hint",after_verified_score=False)
        await asyncio.sleep(0.35)
        self.assertEqual(requested,[],
            "Only a verified score may preempt a busy provider turn")
        await coord.close()

    async def test_new_card_supersedes_stale_provider_reply_but_same_card_does_not(self):
        old=("turn","explainer","огненная шляпа",0,False)
        new=("turn","explainer","воздушный шар",1,False)
        active=[old]
        sent=[]
        commentary=[]
        waiting=asyncio.get_running_loop().create_future()
        second_waiting=asyncio.get_running_loop().create_future()
        def generate(text):
            sent.append(text)
            return waiting if len(sent)==1 else second_waiting
        coord=CardPromptCoordinator(
            generate=generate,commentary=commentary.append,
            state=lambda:(("thinking" if active[0]==new else "listening"),
                          "listening"),
            is_current=lambda key:key==active[0],spoke_at=lambda:0.0,
            response_timeout=0.04,commentary_grace=0.15,attempts=2)
        coord.schedule(old,"first clue")
        await asyncio.wait_for(coord._task,timeout=2)
        self.assertEqual(sent,["first clue"])
        self.assertFalse(waiting.done(), "SDK request remains alive after timeout")
        active[0]=new
        coord.schedule(new,"fresh word")
        await asyncio.wait_for(coord._task,timeout=2)
        self.assertEqual(sent,["first clue","fresh word"],
                         "New SERVER card supersedes the stale model response")
        self.assertFalse(waiting.done(),
                         "Underlying old provider handle remains intact")
        self.assertIn("fresh word",commentary)
        # Same-card repeat must not create another competing response.
        coord.schedule(new,"refine same card")
        await asyncio.wait_for(coord._task,timeout=2)
        self.assertEqual(sent,["first clue","fresh word"])
        waiting.set_result(None)
        second_waiting.set_result(None)
        await coord.close()

    async def test_pending_provider_never_paraphrases_repeated_clock_nudges(self):
        key=("turn","explainer","огненная шляпа",2,False)
        pending=asyncio.get_running_loop().create_future()
        generated=[]
        commentary=[]
        coord=CardPromptCoordinator(
            generate=lambda txt:generated.append(txt) or pending,
            commentary=commentary.append,
            state=lambda:("listening","listening"),
            is_current=lambda k:k==key,spoke_at=lambda:0.0,
            attempts=3,response_timeout=2.1,commentary_grace=0.15)
        coord.schedule(key,"first clue")
        await asyncio.wait_for(coord._task,timeout=3)
        self.assertEqual(generated,["first clue"])
        self.assertEqual(commentary,["first clue"],
            "Commentary is AUDIBLE; never inject countdown or repeated coaching")
        self.assertFalse(pending.done())
        pending.set_result(None)
        await coord.close()

    async def test_no_rapid_retry_when_realtime_reply_is_pending(self):
        key=("turn","explainer","маяк",1,False)
        calls=[]
        pending=asyncio.get_running_loop().create_future()
        coord=CardPromptCoordinator(generate=lambda text:calls.append(text) or pending,
            state=lambda:("listening","listening"),is_current=lambda k:k==key,
            spoke_at=lambda:0.0,attempts=3,response_timeout=0.04)
        coord.schedule(key,"say hint")
        await asyncio.wait_for(coord._task,timeout=2)
        self.assertEqual(len(calls),1)
        self.assertFalse(pending.done())
        coord.schedule(key,"another hint")
        await asyncio.wait_for(coord._task,timeout=2)
        self.assertEqual(len(calls),1)
        pending.set_result(None)
        await coord.close()

    async def test_native_gpt_live_commentary_starts_voice_without_second_request(self):
        key=("r1","explainer","Пингвин",1,False)
        spoke=[0.0]
        called=[]
        async def no_wait(_):
            await asyncio.sleep(0)
        async def no_reply():
            return None
        coord=CardPromptCoordinator(generate=lambda text:called.append(text) or asyncio.create_task(no_reply()),
            commentary=lambda text:spoke.__setitem__(0,1.0),
            state=lambda:("listening","listening"),is_current=lambda k:k==key,
            spoke_at=lambda:spoke[0],require_voice=True,sleep=no_wait)
        coord.schedule(key,"Живая подсказка")
        await coord._task
        self.assertEqual(called,[])
        await coord.close()

    async def test_successful_model_handle_without_audio_is_retried(self):
        key=("r1","explainer","Пингвин",2,False)
        spoke=[0.0]
        calls=[]
        async def no_wait(_):
            await asyncio.sleep(0)
        def generate(text):
            calls.append(text)
            if len(calls)==2:
                spoke[0]=2.0
            return asyncio.create_task(no_wait(0))
        coord=CardPromptCoordinator(generate=generate,
            state=lambda:("listening","listening"),is_current=lambda k:k==key,
            spoke_at=lambda:spoke[0],require_voice=True,sleep=no_wait)
        coord.schedule(key,"Подскажи")
        await coord._task
        self.assertEqual(len(calls),2)
        await coord.close()

    async def test_old_word_commentary_aborts_on_card_change(self):
        old=("r","explainer","Музей",1,False)
        new=("r","explainer","Пингвин",2,False)
        current=[old]
        requested=[]
        async def yield_(secs):
            await asyncio.sleep(0)
        coord=CardPromptCoordinator(
            generate=lambda txt:requested.append(txt) or asyncio.create_task(yield_(0)),
            commentary=lambda txt:requested.append("comment:"+txt),
            state=lambda:("listening","listening"),
            is_current=lambda k:k==current[0],spoke_at=lambda:0.0,sleep=yield_)
        coord.schedule(old,"old")
        current[0]=new
        coord.schedule(new,"new")
        await coord._task
        self.assertTrue(requested)
        self.assertTrue(all("old" not in x for x in requested))
        await coord.close()

    async def test_verified_score_reawakens_silent_new_card_once(self):
        key = ("turn", "explainer", "Маяк", 7, False)
        gate = asyncio.Event()
        comments = []
        replies = []
        async def controlled_sleep(seconds):
            if seconds == 2.4:
                await gate.wait()
            else:
                await asyncio.sleep(0)
        coord = CardPromptCoordinator(
            generate=lambda text: replies.append(text) or asyncio.create_task(asyncio.sleep(0)),
            commentary=comments.append,
            state=lambda: ("listening", "listening"),
            is_current=lambda k: k == key,
            spoke_at=lambda: 0.0, sleep=controlled_sleep,
            attempts=1)
        coord.schedule(key, "first clue", after_verified_score=True)
        await asyncio.sleep(0)
        self.assertEqual(comments, ["first clue"])
        gate.set()
        await asyncio.wait_for(coord._score_silence_task, timeout=1)
        self.assertEqual(len(comments), 2, "One native hint reminder after actual silence")
        self.assertIn("признаком", comments[1])
        self.assertLessEqual(len(replies), 1,
            "A liveness nudge must never request a competing model response")
        await coord.close()

    async def test_verified_score_does_not_reprompt_after_audio_started(self):
        key = ("turn", "explainer", "Кедр", 8, False)
        gate = asyncio.Event()
        spoken = [0.0]
        comments = []
        async def controlled_sleep(seconds):
            if seconds == 2.4:
                await gate.wait()
            else:
                await asyncio.sleep(0)
        coord = CardPromptCoordinator(
            generate=lambda _: asyncio.create_task(asyncio.sleep(0)),
            commentary=comments.append,
            state=lambda: ("listening", "listening"),
            is_current=lambda k: k == key,
            spoke_at=lambda: spoken[0], sleep=controlled_sleep, attempts=1)
        coord.schedule(key, "first clue", after_verified_score=True)
        await asyncio.sleep(0)
        spoken[0] = 1.0
        gate.set()
        await asyncio.wait_for(coord._score_silence_task, timeout=1)
        self.assertEqual(comments, ["first clue"])
        await coord.close()

    async def test_verified_score_recovery_never_speaks_stale_card(self):
        old = ("turn", "explainer", "Маяк", 8, False)
        new = ("turn", "explainer", "Слива", 9, False)
        current = [old]
        gate = asyncio.Event()
        comments = []
        async def controlled_sleep(seconds):
            if seconds == 2.4:
                await gate.wait()
            else:
                await asyncio.sleep(0)
        coord = CardPromptCoordinator(
            generate=lambda _: asyncio.create_task(asyncio.sleep(0)),
            commentary=comments.append,
            state=lambda: ("listening", "listening"),
            is_current=lambda k: k == current[0],
            spoke_at=lambda: 0.0, sleep=controlled_sleep, attempts=1)
        coord.schedule(old, "old clue", after_verified_score=True)
        await asyncio.sleep(0)
        current[0] = new
        coord.schedule(new, "new clue", after_verified_score=True)
        await asyncio.sleep(0)
        gate.set()
        await asyncio.wait_for(coord._score_silence_task, timeout=1)
        self.assertEqual(comments.count("old clue"), 1)
        self.assertEqual(len(comments), 3,
            "Only the current word may receive a post-score recovery")
        await coord.close()

    async def test_stale_speaking_state_does_not_suppress_next_card_cue(self):
        # Observed on Nov 18: old GPT-Live audio kept agent_state=speaking,
        # with no NEW-card onset event. The previous recovery returned here.
        key=("turn","explainer","следующее",5,False)
        gate=asyncio.Event()
        commentary=[]
        async def controlled_sleep(seconds):
            if seconds == 2.4:
                await gate.wait()
            else:
                await asyncio.sleep(0)
        coord=CardPromptCoordinator(
            generate=lambda _:asyncio.create_task(asyncio.sleep(0)),
            commentary=commentary.append,
            state=lambda:("speaking","listening"),
            is_current=lambda k:k==key,
            spoke_at=lambda:10.0,
            sleep=controlled_sleep,
            attempts=1)
        coord.schedule(key,"New clue",after_verified_score=True)
        await asyncio.sleep(0)
        gate.set()
        await asyncio.wait_for(coord._score_silence_task,timeout=1.0)
        self.assertEqual(len(commentary),2)
        self.assertIn("признаком",commentary[1])
        await coord.close()
