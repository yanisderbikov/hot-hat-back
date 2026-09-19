package ru.hothat.sabotage.store;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

/**
 * Боезапас: строка на вид патрона.
 *
 * <p>Заменяет jsonb {@code arsenal} — карту «вид → количество» в строке места
 * игрока. Списание становится одним оператором:
 * {@code UPDATE … SET amount = amount - 1 WHERE … AND amount > 0}; ноль
 * изменённых строк — это {@code NO_AMMO}. Сегодня проверка и списание
 * разнесены (прочитали карту, посчитали, записали карту целиком), и два
 * быстрых выстрела списывают один патрон.
 *
 * <p>Ограничения на набор видов патрона в базе НЕТ намеренно, и это прямое
 * следствие §6.3: источник правды об оружии — {@code WeaponRegistry} в коде,
 * вторая копия набора в базе неминуемо с ним разойдётся. Один раз это уже
 * произошло — список ключей разошёлся с базовым арсеналом, и расчёт падал с
 * NPE на ключе, которого нет.
 *
 * <p>{@code @Version} нет намеренно (§6.4): согласованность даёт условный
 * атомарный {@code UPDATE}, а не сравнение версий.
 */
@Entity
@Table(name = "match_player_ammo", schema = "v2")
@IdClass(PlayerAmmoId.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
class PlayerAmmo {

    @Id
    @Column(name = "match_id", nullable = false, updatable = false)
    private UUID matchId;

    @Id
    @Column(name = "player_id", nullable = false, updatable = false)
    private UUID playerId;

    /** Ключ боезапаса из {@code WeaponRegistry}: их десять на тринадцать оружий. */
    @Id
    @Column(name = "ammo_type", nullable = false, updatable = false, length = 16)
    private String ammoType;

    @Builder.Default
    @Column(nullable = false)
    private Integer amount = 0;
}
