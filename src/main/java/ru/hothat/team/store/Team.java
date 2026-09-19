package ru.hothat.team.store;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * Рейтинговая команда: постоянная пара игроков одного дивизиона.
 *
 * <p>Состава здесь нет — он в {@link TeamMembership}. Сегодня состав лежит в
 * команде двумя jsonb ({@code member_uids} и {@code member_nicknames}) плюс
 * {@code owner_uid}, и третьей копией — в карточке игрока
 * ({@code app_user.ranked_team_id}/{@code ranked_team_status}). Ники —
 * чужие данные: они приходят из карточки и устаревают в тот же миг, когда
 * игрок меняет ник.
 *
 * <p>{@link #nameKey} считает база выражением {@code lower(btrim(name))} —
 * тем же, что и {@code Ids.key}. Поэтому поле только читается. Вместе с ним
 * исчезает таблица {@code ranked_team_name}: «занято ли имя» и «под каким
 * ключом лежит имя» больше не могут разойтись, и ручная синхронизация при
 * роспуске команды не нужна.
 *
 * <p>Колонки {@code rating} нет: сегодняшний jsonb {@code {classic:1000,
 * sabotage:1000}} присваивается при основании и не меняется никогда.
 *
 * <p>{@link #version} — оптимистическая блокировка. Сегодня согласованность
 * команды держится сравнением {@code updatedAt} в документном движке, а это
 * check-then-act (находка B2 аудита): между чтением и записью помещается
 * чужая правка, и потерянное обновление проходит незамеченным.
 */
@Entity
@Table(name = "ranked_team", schema = "v2")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
class Team {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    /** 3–30 символов: то же, что проверяет {@code Ids.TEAM_NAME}. */
    @Column(nullable = false, length = 30)
    private String name;

    @Column(name = "name_key", length = 30, insertable = false, updatable = false)
    private String nameKey;

    @Column(name = "division_language", nullable = false, length = 8)
    private String divisionLanguage;

    /** pending | active — набор закрыт ограничением базы. */
    @Builder.Default
    @Column(nullable = false, length = 16)
    private String status = "pending";

    @Builder.Default
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    /** Заполняется ровно тогда, когда напарник подтвердил команду. */
    @Column(name = "confirmed_at")
    private Instant confirmedAt;

    @Version
    @Builder.Default
    @Column(nullable = false)
    private Long version = 0L;
}
