package ru.hothat.game.usecase;

import ru.hothat.game.domain.SabotageEvent;

/**
 * События партии, которым позволено опоздать.
 *
 * <p>Оба уезжают наружу — в видеосвязь и в запись, — и оба обязаны случиться
 * после фиксации транзакции: держать её открытой на время похода в чужую
 * систему нельзя, а рассылать то, что потом откатится, — тем более. Правило
 * §7.3 плана называет ровно этот случай: событием выражается то, что вправе
 * опоздать, но никогда — инвариант.
 */
public final class MatchEvents {

    private MatchEvents() {
    }

    /** Диверсия применена: разослать её сцене. */
    public record SabotageApplied(String roomId, SabotageEvent event) {
    }

    /** Партия кончилась технически: остановить запись. */
    public record MatchTerminated(String roomId, int gameNumber, String reason) {
    }
}
