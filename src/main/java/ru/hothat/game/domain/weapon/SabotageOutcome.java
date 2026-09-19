package ru.hothat.game.domain.weapon;

import ru.hothat.game.domain.SabotageEvent;

import java.util.Map;

/**
 * Итог применения оружия: что увидят на сцене и что осталось у стрелка.
 *
 * <p>Боезапас едет в ответе целиком, а не изменением одного ключа: панель
 * арсенала рисует все десять счётчиков сразу, и по одному ключу ей пришлось бы
 * тут же перечитывать остальное.
 */
public record SabotageOutcome(SabotageEvent event, Map<String, Integer> arsenal, long cooldownUntilMs) {
}
