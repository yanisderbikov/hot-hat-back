package ru.hothat.room.store;

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
 * Паспорт комнаты: то, что задают при создании и почти никогда не меняют.
 *
 * <p>Четырнадцать колонок из ста трёх, что лежали в {@code room}. Всё
 * остальное разъехалось по частоте записи: крупная фаза — в
 * {@link RoomLifecycle}, хозяйство — в {@link RoomHost}, сердцебиение — в
 * {@link SeatPresence}, состояние хода — в {@code game.store}, боезапас — в
 * {@code sabotage.store}.
 *
 * <p>Имя класса не {@code Room}: так называется легаси-сущность
 * {@code ru.hothat.model.room.Room}, и две сущности с одним именем Hibernate
 * не разводит. Имя освободится вместе со старой таблицей.
 *
 * <p>{@link #createdBy} неизменяемо, и это главное здесь. Сегодня в той же
 * колонке хранится хозяйство комнаты, и передача хозяина её переписывает —
 * из-за чего дыра A1 аудита («сервер верит клиенту, кто хозяин») означает
 * буквально захват комнаты вместе с авторством.
 *
 * <p>{@link #version} — оптимистическая блокировка (§6.4 плана). Сегодня
 * согласованность держится сравнением {@code updatedAt} в документном движке,
 * а это check-then-act (находка B2): между чтением и записью помещается чужая
 * правка.
 */
@Entity
@Table(name = "room", schema = "v2")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
class RoomPassport {

    /** regular | test | team | matchmaking — набор закрыт ограничением базы. */
    static final String REGULAR = "regular";
    static final String TEST = "test";

    /** Код вида {@code hat-[0-9a-f]{16}}: он ходит по чатам ссылкой-приглашением. */
    @Id
    @Column(nullable = false, updatable = false, length = 24)
    private String id;

    /**
     * Вместо трёх булевых флагов {@code is_test_room}, {@code team_lobby} и
     * {@code managed_matchmaking}: они позволяли выразить бессмыслицу —
     * тестовое лобби команды под управлением подбора.
     */
    @Builder.Default
    @Column(nullable = false, length = 16)
    private String kind = REGULAR;

    @Column(nullable = false, length = 80)
    private String name;

    /**
     * Одна вместимость вместо {@code max_players} и {@code max_participants}.
     * Сегодня их две, и они уже разошлись: проекция берёт вместимость то из
     * одной, то из другой.
     */
    @Builder.Default
    @Column(nullable = false)
    private Integer capacity = 10;

    /**
     * Миллисекунды вместо пары {@code turn_duration} (секунды, целое) и
     * {@code turn_duration_seconds} (дробное). Секунды контракта считает DTO.
     */
    @Builder.Default
    @Column(name = "turn_duration_ms", nullable = false)
    private Integer turnDurationMs = 60000;

    /** classic | sabotage — тот же набор, что {@code GameMode} в контракте. */
    @Builder.Default
    @Column(name = "game_mode", nullable = false, length = 16)
    private String gameMode = "classic";

    @Builder.Default
    @Column(nullable = false)
    private Boolean ranked = false;

    @Builder.Default
    @Column(name = "private_room", nullable = false)
    private Boolean privateRoom = false;

    /** Дивизион, по которому пускают в рейтинговую партию. */
    @Builder.Default
    @Column(name = "division_language", nullable = false, length = 8)
    private String divisionLanguage = "ru";

    /** Язык слов: у быстрой комнаты он может отличаться от дивизиона. */
    @Builder.Default
    @Column(name = "game_language", nullable = false, length = 8)
    private String gameLanguage = "ru";

    /** Комната, заведённая постоянной командой; пусто — обычная комната. */
    @Column(name = "ranked_team_id")
    private UUID rankedTeamId;

    /** Кто завёл комнату. Не хозяин: хозяйство живёт в {@link RoomHost}. */
    @Column(name = "created_by", nullable = false, updatable = false)
    private UUID createdBy;

    @Builder.Default
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Version
    @Builder.Default
    @Column(nullable = false)
    private Long version = 0L;
}
