# HOT-HAT — GPT-Live 1 voice agent (SCRUM-50)

**Status:** isolated voice-agent foundation only; **not** deployed or dispatched;
**not** yet a game-playing AI. Existing scripted test bots stay unchanged.

## Model source

- The first selected model is **GPT-Live 1** (full duplex), not GPT-Live 1.1
  from consumer ChatGPT and not a substitution with a mini model.
- **Primary:** LiveKit Inference `openai/gpt-live-1`, using
  `LIVEKIT_URL`, `LIVEKIT_API_KEY`, `LIVEKIT_API_SECRET`.
  No separate `OPENAI_API_KEY`. Usage billed by LiveKit Cloud.
- **Alternative:** LiveKit Agents' OpenAI plugin `GPTLiveModel` with a paid
  OpenAI API key and direct OpenAI billing.
- Official sources:
  https://docs.livekit.io/agents/models/realtime/gpt-live/
  https://docs.livekit.io/agents/server/agent-dispatch/
  https://platform.openai.com/pricing/

Direct OpenAI GPT-Live 1 voice session list price is $0.05/min;
the Responses reasoning backend and tools have separate charges.
**Do not assume LiveKit Inference has identical retail pricing.**

## Safe local development

Use Python 3.11+ in this directory:

```bash
python -m unittest -v test_config.py  # offline, never bills
pip install -r requirements.txt
```

For an **explicitly authorized future voice test** only:

```text
HOT_HAT_GPT_LIVE_ENABLED=true
HOT_HAT_GPT_LIVE_MAX_SECONDS=600  # hard capped at 600
HOT_HAT_GPT_LIVE_VOICE=marin
LIVEKIT_URL=wss://<livekit-host>
LIVEKIT_API_KEY=<private>
LIVEKIT_API_SECRET=<private>
```

Start with `python agent.py start`. The worker is registered under
`hot-hat-gpt-live` using **named explicit dispatch**. It NEVER joins
arbitrary HOT-HAT rooms automatically. An authorized backend action must call
LiveKit AgentDispatchService with that name and the exact room identifier.
No such dispatch endpoint or UI is installed yet, and no paid worker is deployed.
Keep credentials in server variables, never the browser or repository.

## Next integration gates — NOT yet implemented

1. Test that GPT-Live can hear and speak Russian via a real explicitly dispatched
   LiveKit voice job. Agent audio-room mixing for multiple simultaneous human
   speakers must be tested, not inferred from single-caller examples.
2. Add Java server-authenticated game tools (current word/role context,
   validated guess submission, safe explanation hint, authorized sabotage and
   phase updates). No model-supplied score or turn timer may be accepted.
3. Add UI to explicitly invite the bot as a named AI participant and show
   a distinct AI avatar; do not treat it as a normal human webcam or the
   current scripted `testbot-*` identities.
4. Training: human explains -> AI guesses; AI explains -> human guesses.
   Each action is validated by the Java game engine. Then extend to full
   standard match play; AI test matches do not affect normal rankings.
5. Enforce opt-in, active-session budget/time limits, room-level concurrency,
   visible start/stop, authenticated dispatch and no auto-restart charges.
   A full voice session is not free even if nobody is speaking.
6. Review audio/data handling for Russian players and relevant 152-FZ
   obligations (see SCRUM-39). Current Railway and LiveKit infrastructure is
   outside Russia; a Russian speech proxy alone would not solve the issue.

The agent's current voice instructions forbid fabricated game results; **there
are no live game-action tools yet**. Do not advertise it as playable until a
real end-to-end voice/game validation succeeds on two computers.

## SCRUM-50 — Рублиша / smol talk

Рублиша — женский голосовой ИИ-участник. Game-сессия запускается только
на ходе своей команды. Кнопка владельца вызывает
`POST /api/v2/room/{roomId}/ai-players/{botUid}/smalltalk` и завершает
`DELETE` по тому же адресу. Только JWT с ролью OWNER и собственным
реальным местом игрока в этой комнате допускается к разговорам вне хода.
В smalltalk-сессиях игровые инструменты отвергаются бэкендом с 403,
секретные слова не выдаются. У каждого подключения потолок 180 секунд,
сохраняется единственная оплачиваемая сессия и месячный бюджет.

Флаг `shouldGreet` вычисляется сервером по уже начатым сессиям в
`ai_voice_session` для (roomId, gameNumber). Повторные задания того же
матча НЕ приветствуют заново. Новый номер партии сбрасывает правило.
