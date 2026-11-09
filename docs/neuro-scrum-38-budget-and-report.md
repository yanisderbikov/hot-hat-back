# SCRUM-38 — final owner bot-room paid test runbook (2026-11-10)

## Target
In a bot test room the actual **room owner** is the victim. Record up to 30 s of the owner's webcam and microphone on the owner's first turn; convert a selected 7.5 s reference plus first JPEG frame into a freshly acted Kling O3 reference-to-video output. Do NOT apply the result to a bot or swap in the raw recording.

## One-test budget
Owner-reported available balance: **766 ₽**. GenAPI's published O3 Pro **video-input** tariff is **84 ₽ per output second** (the previously quoted 56 ₽ applies without input video). Ten seconds would cost 840 ₽ for video alone and must not be offered for this fixed-budget test. The bot frontend computes its maximum duration from 766 ₽ minus 20 ₽ for voice/TTS and a Lip Sync reserve at 200 ₽/minute: max 8 s. Video 672 ₽; Lip Sync ~27 ₽; other stages reserved 20 ₽; planned total ~719 ₽. This is a planning estimate, NOT a live wallet lock or provider guarantee. Preflight and paid upload both pass `maxDurationSeconds=8`. The backend persists the cap and recomputes the same scene for later jobs/recovery, so no silent 10-second call. The text of all spoken words is retained; secondary gestures may be deprioritized.

## One payment, live stages and playback
A free plan/readiness/history check precedes consent. Owner explicitly confirms the only new paid job; the API retains that job ID. The browser displays stage, elapsed time, GenAPI IDs and per-owner-round waiting notices. Play only the complete generated MP4 on the next suitable subsequent owner turn, not necessarily round two or three if GenAPI takes longer. Browser localStorage persists waiting rounds, playback turn, played timestamp, stage transitions and bounded polling/playback errors, all for the same job and room.

## Evidence and diagnostics
Owner-only `/api/v2/admin/neuro-replacements` history provides source camera video, 7.5-second Kling input clip, actual JPEG `/starting-frame` if generated, final MP4, raw typed assignment, effective normalized assignment, exact submitted O3 prompt, prompt adaptation, speech, time window, chronological stage checkpoints, provider request IDs/timestamps, provider status, reported costs (if available) and error messages.

Download `/{jobId}/diagnostics/report` as JSON from the **same browser** where the game was played; the read-only archive merges local playback evidence into that JSON. Report includes only available diagnostic data, not secrets or raw Railway operational logs. Missing files or transient GenAPI live-check outages should not trigger a new paid request.

## Completion criteria
Do not mark SCRUM-38 complete until a real owner webcam/mic run, paid generation, bot-room playback and archived media/diagnostics are inspected. Automatic tests and SUCCESS Railway deployments validate plumbing but cannot establish that Kling physically picked up the keyboard, performed the rap or returned the prop.
