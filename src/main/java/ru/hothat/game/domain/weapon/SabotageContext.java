package ru.hothat.game.domain.weapon;

import ru.hothat.game.domain.MatchPlayer;
import ru.hothat.game.domain.MatchState;
import ru.hothat.game.domain.RandomSource;

/**
 * Всё, что обработчику оружия нужно знать: партия, стрелок, просьба, время.
 *
 * <p>Время приходит числом, а не берётся из часов внутри: обработчик должен
 * решать одинаково при одном и том же моменте, иначе «эффект не переживает
 * ход» проверить нечем.
 */
public record SabotageContext(MatchState state,
                              MatchPlayer attacker,
                              SabotageCommand command,
                              long nowMs,
                              RandomSource random) {
}
