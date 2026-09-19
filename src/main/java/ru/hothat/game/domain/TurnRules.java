package ru.hothat.game.domain;

/**
 * Часы хода: когда он кончается и когда поздно засчитывать слово.
 *
 * <p>Всё время здесь — серверное, приходящее аргументом. Клиентские часы
 * отличались от серверных настолько, что фронт держал целую процедуру
 * калибровки ({@code app-core.js:8053-8062}); решать по ним, истёк ли ход,
 * значило отдать длительность хода тому, чьи часы спешат.
 */
public final class TurnRules {

    /** Сколько идёт голосование по спорным словам. */
    public static final long APPEAL_MS = 10_000;

    /**
     * Отсрочка последнего слова. Игрок нажимает «Угадали» в момент, когда ход
     * уже истёк, — это не жульничество, а сетевая задержка и человеческая
     * реакция. Три секунды засчитываются, но ход после них закрывается.
     */
    public static final long LAST_WORD_GRACE_MS = 3000;

    /** Длительность хода по умолчанию, если комната её не назвала. */
    public static final double DEFAULT_DURATION_SECONDS = 60;

    private TurnRules() {
    }

    /** Длительность именно этого хода: после снятия паузы она равна остатку. */
    public static double durationSeconds(MatchState state) {
        Double own = state.getTurnDurationSeconds();
        if (own != null && own > 0) {
            return own;
        }
        double byRoom = state.getDefaultTurnDurationSeconds();
        return byRoom > 0 ? byRoom : DEFAULT_DURATION_SECONDS;
    }

    /**
     * Дедлайн хода. Считается от начала хода, а не хранится готовым: пауза
     * переписывает длительность, и заранее посчитанный дедлайн после неё врал бы.
     * Хвост с {@code legacyTurnEndsAt} нужен комнатам, начатым прошлой версией.
     */
    public static long deadline(MatchState state) {
        Long startedAt = state.getTurnStartedAtMs();
        double seconds = durationSeconds(state);
        if (startedAt != null && startedAt > 0 && seconds > 0) {
            return startedAt + Math.round(seconds * 1000);
        }
        return Math.max(0, state.getLegacyTurnEndsAt());
    }

    public static long remainingMs(MatchState state, long nowMs) {
        long deadline = deadline(state);
        if (deadline <= 0) {
            return Math.round(durationSeconds(state) * 1000);
        }
        return Math.max(0, deadline - nowMs);
    }

    /** Ход истёк вместе с отсрочкой последнего слова: пора закрывать. */
    public static boolean expired(MatchState state, long nowMs) {
        long deadline = deadline(state);
        return deadline > 0 && nowMs > deadline + LAST_WORD_GRACE_MS;
    }

    /** Ход истёк, но слово ещё засчитывается — и станет последним. */
    public static boolean inLastWordGrace(MatchState state, long nowMs) {
        long deadline = deadline(state);
        return deadline > 0 && nowMs > deadline && nowMs <= deadline + LAST_WORD_GRACE_MS;
    }

    /** Хватит ли остатка хода на эффект, который длится {@code needMs}. */
    public static boolean fitsInTurn(MatchState state, long nowMs, long needMs) {
        return needMs <= 0 || remainingMs(state, nowMs) >= needMs;
    }
}
