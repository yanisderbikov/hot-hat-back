package ru.hothat.game.domain.weapon;

import ru.hothat.game.domain.SabotageEvent;

/**
 * Что решил обработчик оружия.
 *
 * <p>Само по себе решение ничего не меняет: боезапас, перезарядку, дорожки
 * эффектов и историю правит {@code SabotageEngine}. Так сделано, чтобы
 * тринадцать обработчиков не повторяли тринадцать раз одно и то же списание
 * заряда — расхождение как раз в нём и накапливалось.
 *
 * @param consumedClipId клип Подмены, который сгорел при применении
 */
public record SabotageDecision(SabotageEvent event, long lockUntilMs, boolean consumeMeme, String consumedClipId) {

    public static SabotageDecision of(SabotageEvent event, long lockUntilMs) {
        return new SabotageDecision(event, lockUntilMs, false, null);
    }
}
