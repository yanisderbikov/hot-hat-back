package ru.hothat.game.domain.weapon;

import ru.hothat.config.ApiException;
import ru.hothat.game.domain.MatchPlayer;
import ru.hothat.game.domain.MatchState;
import ru.hothat.game.domain.RandomSource;
import ru.hothat.game.domain.ReplacementClip;
import ru.hothat.game.domain.SabotageEvent;
import ru.hothat.game.domain.TurnIds;

import java.util.ArrayList;
import java.util.List;

/**
 * Жизнь клипа Подмены: съёмка, итог, отказ.
 *
 * <p>Итог съёмки присылает не тот, кто снимал, а тот, кого снимали: плёнка
 * пишется в его браузере, и знать, вышел ли кадр, может только он. Это и есть
 * тот самый инвариант владения, который живёт внутри сценария (§7.4 плана):
 * проверить его можно, только прочитав сам клип.
 */
public final class ReplacementClipRules {

    /** Сколько клипов может держать один атакующий одновременно. */
    public static final int SLOTS = 3;
    /** Длина съёмки: десять секунд плюс полсекунды на запуск. */
    public static final long RECORD_MS = 10_500;
    /** Длительность события съёмки, которую показывает сцена. */
    public static final long RECORD_EVENT_MS = 10_000;

    private ReplacementClipRules() {
    }

    /** Сколько клипов этой партии держит атакующий. */
    public static int ownedBy(MatchState state, String attackerUid) {
        int count = 0;
        for (ReplacementClip clip : state.getClips().values()) {
            if (clip.attackerUid().equals(attackerUid) && clip.gameNumber() == state.getGameNumber()) {
                count++;
            }
        }
        return count;
    }

    /**
     * Брошенные съёмки освобождают слот.
     *
     * <p>Иначе закрытая посреди съёмки вкладка занимала бы слот до конца
     * партии: итог прислать уже некому.
     */
    public static void dropAbandoned(MatchState state, long nowMs) {
        List<String> abandoned = state.getClips().values().stream()
                .filter(clip -> clip.abandoned(nowMs))
                .map(ReplacementClip::id)
                .toList();
        abandoned.forEach(id -> state.getClips().remove(id));
    }

    /** Начать съёмку: клип занимает слот, а атакующий — свою дорожку съёмки. */
    public static SabotageEvent order(MatchState state, MatchPlayer attacker, long nowMs, RandomSource random) {
        String clipId = TurnIds.clip(random);
        ReplacementClip clip = new ReplacementClip(clipId, attacker.getUid(), state.getExplainerUid(),
                state.getTurnId(), state.getGameNumber(), nowMs, null);
        state.getClips().put(clipId, clip);
        // Блокировка поимённая: десять игроков вправе снимать одновременно.
        state.getLocks().startRecording(attacker.getUid(), nowMs + RECORD_MS);
        SabotageEvent event = new SabotageEvent(TurnIds.sabotage(random), SabotageEvent.RECORD_TYPE, null, clipId,
                state.getTurnId(), attacker.getUid(), attacker.displayName(), state.getExplainerUid(),
                nowMs, RECORD_EVENT_MS, state.getGameNumber(), null, null,
                null, null, null, null, null, null, null, null);
        remember(state, event);
        return event;
    }

    /**
     * Съёмка попадает в историю партии наравне с выстрелами: сцена по ней
     * рисует индикатор «идёт съёмка», а рекордер — то же самое в записи.
     */
    private static void remember(MatchState state, SabotageEvent event) {
        List<SabotageEvent> recent = new ArrayList<>(state.getRecentEvents());
        recent.add(event);
        List<SabotageEvent> current = new ArrayList<>();
        for (SabotageEvent item : recent) {
            if (item.gameNumber() == state.getGameNumber()) {
                current.add(item);
            }
        }
        if (current.size() > SabotageEvent.RECENT_LIMIT) {
            current = new ArrayList<>(current.subList(current.size() - SabotageEvent.RECENT_LIMIT, current.size()));
        }
        state.setRecentEvents(current);
        state.setLastEvent(event);
    }

    /** Итог съёмки: удачную плёнку сохраняем, неудачную стираем. */
    public static ReplacementClip settle(MatchState state, String clipId, String reporterUid, boolean ready,
                                         long nowMs) {
        ReplacementClip clip = state.getClips().get(clipId);
        if (clip == null || !clip.targetUid().equals(reporterUid)) {
            // Один ответ и на чужой клип, и на несуществующий: по номеру клипа
            // нельзя узнать, снимали ли кого-то ещё.
            throw ApiException.of("REPLACEMENT_CLIP_INVALID", 404);
        }
        state.getLocks().stopRecording(clip.attackerUid());
        if (!ready) {
            state.getClips().remove(clipId);
            return null;
        }
        ReplacementClip settled = clip.readyAt(nowMs);
        state.getClips().put(clipId, settled);
        return settled;
    }

    /** Отказ от своего клипа: слот освобождается сразу. */
    public static void discard(MatchState state, String clipId, String attackerUid) {
        ReplacementClip clip = state.getClips().get(clipId);
        if (clip == null || !clip.attackerUid().equals(attackerUid)) {
            throw ApiException.of("REPLACEMENT_CLIP_INVALID", 404);
        }
        state.getClips().remove(clipId);
        state.getLocks().stopRecording(attackerUid);
    }
}
