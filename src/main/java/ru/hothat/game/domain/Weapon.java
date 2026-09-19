package ru.hothat.game.domain;

/**
 * Описание одного вида оружия.
 *
 * <p>Раньше эти же сведения были размазаны по {@code useSabotage}: длительность
 * считалась одним {@code switch}, дорожка эффекта — цепочкой {@code if},
 * ключ боезапаса — тернарной лестницей, а базовый запас лежал отдельной картой,
 * которая с этим списком не сверялась. Здесь всё оружие описано одной строкой,
 * поэтому расхождение между списком и запасом стало невыразимым.
 *
 * @param type                      вид оружия
 * @param ammoKey                   ключ боезапаса в арсенале игрока; {@code null} — оружие без заряда
 * @param baseAmmo                  сколько зарядов выдаётся на партию
 * @param durationMs                длительность эффекта; {@link #DURATION_FROM_MEDIA} — берётся из мема,
 *                                  {@link #DURATION_TO_TURN_END} — до конца хода
 * @param lock                      дорожка, которую занимает эффект
 * @param advanced                  расширенный арсенал: только режим диверсий и не в тестовой комнате
 * @param ownerOnly                 доступно только владельцу сервиса
 * @param ownerUnlimited            у владельца не расходуется в ноль
 * @param minimumTurnRemainingMs    сколько времени хода должно остаться, иначе эффект не поместится
 * @param cooldownExempt            не подчиняется общей перезарядке и не запускает её
 * @param allowedDuringReplacement  разрешено во время чужой Подмены
 * @param targetsExplainer          бьёт по объясняющему (все, кроме Подмены: та бьёт по снятому игроку)
 */
public record Weapon(WeaponType type,
                     String ammoKey,
                     int baseAmmo,
                     long durationMs,
                     EffectLock lock,
                     boolean advanced,
                     boolean ownerOnly,
                     boolean ownerUnlimited,
                     long minimumTurnRemainingMs,
                     boolean cooldownExempt,
                     boolean allowedDuringReplacement,
                     boolean targetsExplainer) {

    /** Длительность приезжает из метаданных мема: у каждого ролика она своя. */
    public static final long DURATION_FROM_MEDIA = 0;
    /** Эффект держится до конца хода — так работает крокодил. */
    public static final long DURATION_TO_TURN_END = -1;

    public boolean consumesAmmo() {
        return ammoKey != null;
    }
}
