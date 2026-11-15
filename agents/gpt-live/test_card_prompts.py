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

    async def test_busy_peer_does_not_discard_next_card(self):
        key=("r1","explainer","Арбуз",3,False)
        called=[]
        async def finish():return None
        coord=CardPromptCoordinator(
            generate=lambda s:called.append(s) or asyncio.create_task(finish()),
            state=lambda:("listening","speaking"),
            is_current=lambda k:k==key,spoke_at=lambda:0.0,
            sleep=lambda _:asyncio.sleep(0))
        coord.schedule(key,"give next hint")
        await asyncio.wait_for(coord._task,timeout=2)
        self.assertEqual(called,["give next hint"])
        await coord.close()
