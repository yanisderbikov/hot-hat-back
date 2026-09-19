package ru.hothat.game.domain.weapon;

import ru.hothat.game.domain.WeaponType;

/**
 * Просьба применить оружие.
 *
 * <p>Три поля из пяти нужны не всякому оружию: мем без {@code memeId} и
 * Подмена без {@code clipId} невозможны, а координаты есть только у накладок.
 * Проверяет это обработчик своего вида оружия — в контроллере ветвлений нет.
 *
 * @param media метаданные мема, вычитанные до входа в домен; для остального оружия — {@code null}
 * @param owner владелец сервиса: у него своя маска и неисчерпаемые негатив с апожем
 */
public record SabotageCommand(WeaponType type,
                              String attackerUid,
                              String memeId,
                              String clipId,
                              Double x,
                              Double y,
                              MemeMedia media,
                              boolean owner) {
}
