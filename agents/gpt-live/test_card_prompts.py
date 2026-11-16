"""Deterministic, free tests for GPT-Live new-card speech coordination."""
import asyncio
import unittest

from card_prompts import CardPromptCoordinator


class CardPromptCoordinatorTests(unittest.IsolatedAsyncioTestCase):
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


    async def test_pending_generate_reply_never_overlaps_next_card(self):
        old=("turn","explainer","огненная шляпа",0,False)
        new=("turn","explainer","воздушный шар",1,False)
        active=[old]
        sent=[]
        commentary=[]
        waiting=asyncio.get_running_loop().create_future()
        async def ready():pass
        def generate(text):
            sent.append(text)
            return waiting if len(sent)==1 else asyncio.create_task(ready())
        coord=CardPromptCoordinator(
            generate=generate,commentary=commentary.append,
            state=lambda:("listening","listening"),
            is_current=lambda key:key==active[0],spoke_at=lambda:0.0,
            response_timeout=0.04,commentary_grace=0.15,attempts=2)
        coord.schedule(old,"first clue")
        await asyncio.wait_for(coord._task,timeout=2)
        self.assertEqual(sent,["first clue"])
        self.assertFalse(waiting.done(), "SDK request remains alive after timeout")
        active[0]=new
        coord.schedule(new,"fresh word")
        await asyncio.wait_for(coord._task,timeout=2)
        self.assertEqual(sent,["first clue"],
                         "Must not start a second reply while the old one is pending")
        self.assertIn("fresh word",commentary)
        waiting.set_result(None)
        coord.schedule(new,"fresh word")
        await asyncio.wait_for(coord._task,timeout=2)
        self.assertEqual(sent,["first clue","fresh word"])
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
