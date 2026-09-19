package ru.hothat.recording.store;

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
 * Паспорт записи партии: то, что не меняется после того, как партия сыграна.
 *
 * <p>{@link #id} — суррогат (§6.1). Сегодня ключ склеен из двух значений
 * («hat-…-3»), и по нему нельзя ни отобрать записи комнаты, ни отсортировать
 * партии по номеру, не разрезая строку.
 *
 * <p>Замороженные условия партии ({@link #gameMode}, {@link #ranked},
 * {@link #privateRoom}, {@link #testRoom}, оба языка, {@link #title})
 * скопированы намеренно и не подпадают под §6.3: запись переживает комнату, а
 * её карточка обязана показывать ту партию, которая на видео, а не сегодняшнее
 * состояние комнаты. По той же причине {@link #roomId} обнуляется, а не уносит
 * запись за собой.
 *
 * <p>Статуса здесь нет вовсе. Сегодня их два и они спорят: строковый
 * {@code status} рядом с числовым {@code livekit_status}. §6.3 сводит их в одно
 * {@link EgressJob#getState()}, а «файл удалён» — это
 * {@link RecordingArtifact#getDeletedAt()}.
 *
 * <p>Счёта победителя и суммы очков тоже нет: они выводятся из
 * {@link RecordingTeam}. Денормализованному счётчику здесь не с чем расходиться.
 *
 * <p>{@code @Version} нет: строка пишется один раз, при старте записи.
 */
@Entity
@Table(name = "recording", schema = "v2")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
class Recording {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    /** Пусто — комнату уже убрали; запись живёт своим сроком. */
    @Column(name = "room_id", length = 24)
    private String roomId;

    @Column(name = "game_number", nullable = false, updatable = false)
    private Integer gameNumber;

    /**
     * Снимок названия комнаты. Пусто — названия не было, и подпись строит
     * клиент из идентификатора; колонка не врёт вместо него.
     */
    @Column(length = 80)
    private String title;

    @Builder.Default
    @Column(name = "game_mode", nullable = false, updatable = false, length = 16)
    private String gameMode = "classic";

    @Builder.Default
    @Column(nullable = false, updatable = false)
    private Boolean ranked = false;

    @Builder.Default
    @Column(name = "private_room", nullable = false, updatable = false)
    private Boolean privateRoom = false;

    @Builder.Default
    @Column(name = "test_room", nullable = false, updatable = false)
    private Boolean testRoom = false;

    @Builder.Default
    @Column(name = "division_language", nullable = false, updatable = false, length = 8)
    private String divisionLanguage = "ru";

    @Builder.Default
    @Column(name = "game_language", nullable = false, updatable = false, length = 8)
    private String gameLanguage = "ru";

    @Builder.Default
    @Column(name = "word_count", nullable = false)
    private Integer wordCount = 0;

    /** Кто нажал «снимать». Без ключа: журнал переживает удаление учётки. */
    @Column(name = "started_by", nullable = false, updatable = false)
    private UUID startedBy;

    @Builder.Default
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();
}
