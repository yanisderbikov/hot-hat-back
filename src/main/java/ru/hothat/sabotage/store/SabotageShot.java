package ru.hothat.sabotage.store;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * Применённая диверсия: строка журнала выстрелов.
 *
 * <p>Заменяет сразу два jsonb: {@code sabotage_event} (последний выстрел) и
 * {@code sabotage_events_recent} (массив последних двадцати четырёх). Одно и
 * то же событие лежало в двух местах, и «последнее» умело не совпасть с
 * последним элементом массива. Здесь и то и другое — один отбор по
 * {@code (match_id, seq DESC)} с разным пределом.
 *
 * <p>Имя класса не {@code SabotageEvent}: так называется запись движка
 * {@code ru.hothat.game.domain.SabotageEvent}.
 *
 * <p>Из события выброшены шесть колонок каталога мемов (заголовок, ссылка,
 * постер, пути и провайдер хранилища): §6.3 числит их копиями чужих данных, и
 * они устаревали в тот же миг, когда владелец переливал ролик. Осталась
 * ссылка {@link #memeId}, каталог читается через {@code MemeCatalogPort}.
 *
 * <p>{@link #seq} — номер выстрела в партии, он же порядок: часы клиентов для
 * этого не годятся, а два выстрела в одну миллисекунду бывают.
 */
@Entity
@Table(name = "sabotage_event", schema = "v2")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
class SabotageShot {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "match_id", nullable = false, updatable = false)
    private UUID matchId;

    @Column(nullable = false, updatable = false)
    private Integer seq;

    @Column(name = "turn_id", updatable = false)
    private UUID turnId;

    /**
     * Код оружия из {@code WeaponType}. Ограничения на набор в базе нет по той
     * же причине, что у вида патрона: каталог живёт в {@code WeaponRegistry}.
     */
    @Column(name = "weapon_type", nullable = false, updatable = false, length = 16)
    private String weaponType;

    @Column(name = "attacker_player_id", nullable = false, updatable = false)
    private UUID attackerPlayerId;

    /** Пусто у оружия, которое бьёт по сцене, а не по человеку. */
    @Column(name = "target_player_id", updatable = false)
    private UUID targetPlayerId;

    @Column(name = "meme_id", updatable = false)
    private UUID memeId;

    @Column(name = "clip_id", updatable = false)
    private UUID clipId;

    @Builder.Default
    @Column(name = "duration_ms", nullable = false, updatable = false)
    private Integer durationMs = 0;

    /**
     * Куда прилетел помидор: доли ширины и высоты сцены, а не пиксели, —
     * у зрителей разные экраны.
     */
    @Column(name = "pos_x", updatable = false)
    private Double posX;

    @Column(name = "pos_y", updatable = false)
    private Double posY;

    @Builder.Default
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();
}
