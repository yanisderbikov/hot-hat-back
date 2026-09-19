package ru.hothat.game.domain;

/**
 * Клип Подмены: десять секунд объясняющего, снятые заранее.
 *
 * <p>Клип живёт от съёмки до применения и привязан к трём вещам сразу:
 * к снявшему ({@code attackerUid}), к снятому ({@code targetUid}) и к ходу,
 * в котором снят ({@code recordedTurnId}). Последнее — не украшение: применить
 * клип в том же ходу, где он снят, значит показать зрителям то, что они
 * только что видели вживую, поэтому такой запрос отвергается.
 *
 * @param readyAtMs момент готовности; {@code null} — съёмка ещё идёт
 */
public record ReplacementClip(String id,
                              String attackerUid,
                              String targetUid,
                              String recordedTurnId,
                              int gameNumber,
                              long createdAtMs,
                              Long readyAtMs) {

    /** Сколько ждём итог съёмки, прежде чем считать слот брошенным. */
    public static final long ABANDONED_AFTER_MS = 20000;

    public boolean ready() {
        return readyAtMs != null;
    }

    public ReplacementClip readyAt(long atMs) {
        return new ReplacementClip(id, attackerUid, targetUid, recordedTurnId, gameNumber, createdAtMs, atMs);
    }

    /** Съёмку начали и бросили: вкладку закрыли, итог так и не пришёл. */
    public boolean abandoned(long nowMs) {
        return !ready() && nowMs - createdAtMs > ABANDONED_AFTER_MS;
    }
}
