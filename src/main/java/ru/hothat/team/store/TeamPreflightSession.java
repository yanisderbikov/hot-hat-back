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
 * Сессия предматчевой проверки: одна на команду, ключ — сама команда.
 *
 * <p>{@link #sessionSeq} — не украшение. Новая проверка полностью заменяет
 * старую, и флаги готовности при этом не переносятся. Пока флаги лежали в
 * jsonb самой сессии, замена была переписыванием документа — достаточно было
 * положить пустые карты. Со строками на участника
 * ({@link TeamPreflightParticipant}) нужен признак поколения, иначе «готов»
 * из прошлой проверки досталось бы новой.
 *
 * <p>{@link #version} — потому что сессию правят трое: оба напарника и
 * подбор. Без блокировки «поиск начался» и «я готов», пришедшие одновременно,
 * затирают друг друга.
 *
 * <p>Отметки времени — {@code timestamptz}, а не миллисекунды {@code bigint},
 * как сегодня: миллисекунды считает DTO на выходе, серверное время у клиента
 * одно.
 */
@Entity
@Table(name = "team_preflight_session", schema = "v2")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
class TeamPreflightSession {

    @Id
    @Column(name = "team_id", nullable = false, updatable = false)
    private UUID teamId;

    @Builder.Default
    @Column(name = "session_seq", nullable = false)
    private Integer sessionSeq = 1;

    /** quick | room — набор закрыт ограничением базы. */
    @Column(nullable = false, length = 8)
    private String intent;

    /** classic | sabotage — набор закрыт ограничением базы. */
    @Column(name = "game_mode", nullable = false, length = 16)
    private String gameMode;

    /**
     * Комната, в которую целится пара при замысле {@code room}. База требует
     * её ровно тогда, когда замысел — {@code room}: замысел без комнаты
     * увозил пару к случайным соперникам, и ради этой ошибки замысел стал
     * перечислением.
     */
    @Column(name = "requested_room_id", length = 24)
    private String requestedRoomId;

    /**
     * Затейщик проверки. Колонка изменяемая: строка на команду одна, и новую
     * проверку может начать второй напарник — тогда затейщик другой. Запрет на
     * изменение означал бы, что первым запустивший пару остаётся её капитаном
     * навсегда.
     */
    @Column(name = "initiator_player_id", nullable = false)
    private UUID initiatorPlayerId;

    /**
     * Когда началась эта проверка. Тоже изменяемая, и по той же причине:
     * новая проверка занимает ту же строку, а «началась пять минут назад» про
     * только что начатую подготовку — прямая ложь на экране.
     */
    @Builder.Default
    @Column(name = "started_at", nullable = false)
    private Instant startedAt = Instant.now();

    @Builder.Default
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    /** Комната, которую нашёл подбор. */
    @Column(name = "target_room_id", length = 24)
    private String targetRoomId;

    @Builder.Default
    @Column(name = "room_ready", nullable = false)
    private Boolean roomReady = Boolean.FALSE;

    @Builder.Default
    @Column(name = "search_started", nullable = false)
    private Boolean searchStarted = Boolean.FALSE;

    @Builder.Default
    @Column(name = "search_count", nullable = false)
    private Integer searchCount = 0;

    @Builder.Default
    @Column(nullable = false)
    private Boolean failed = Boolean.FALSE;

    @Version
    @Builder.Default
    @Column(nullable = false)
    private Long version = 0L;
}
