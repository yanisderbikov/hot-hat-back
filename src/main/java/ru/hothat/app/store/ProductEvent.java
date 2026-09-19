package ru.hothat.app.store;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
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
 * Продуктовое событие клиента.
 *
 * <p>Имя класса не {@code AnalyticsEvent}: так называется легаси-сущность
 * {@code ru.hothat.model.ops.AnalyticsEvent}, а имена сущностей у Hibernate
 * общие на всё приложение, и два одинаковых просто не дали бы приложению
 * подняться.
 *
 * <p>Тело события разобрано на шесть колонок — ровно те шесть полей, которые
 * описывает {@code AnalyticsEventPayloadView} и считает дашборд. Сегодня это
 * jsonb, и статистика периода достаёт из него {@code playerCount},
 * {@code teamCount} и {@code wordCount} разбором json в памяти на каждое из
 * десяти тысяч прочитанных событий.
 *
 * <p>{@link #id} — суррогат, а {@link #eventKey} — ключ идемпотентности:
 * обработчик конца партии срабатывает у каждого участника, и одно событие
 * приезжает на сервер пять раз. Сегодня ключ КЛИЕНТА стоит первичным, то есть
 * строку в таблице называет браузер.
 *
 * <p>Почты здесь нет. Сегодня событие несёт и uid, и почту, хотя почта нужна
 * только чтобы показать её в отчёте: она читается через
 * {@code ProfileDirectoryPort} по игроку, а копия почты в журнале событий
 * пережила бы её смену.
 */
@Entity
@Table(name = "analytics_event", schema = "v2")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
class ProductEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_key", nullable = false, updatable = false, length = 160)
    private String eventKey;

    /** Набор закрыт AnalyticsEventType и повторён ограничением базы. */
    @Column(name = "event_type", nullable = false, updatable = false, length = 40)
    private String eventType;

    /** Событие всегда чьё-то: гостю аналитика не открыта. Без ключа на учётку. */
    @Column(name = "player_id", nullable = false, updatable = false)
    private UUID playerId;

    /** Пусто у событий учётки: у входа в неё комнаты нет. */
    @Column(name = "room_id", updatable = false, length = 24)
    private String roomId;

    @Column(name = "room_name", updatable = false, length = 80)
    private String roomName;

    /**
     * Обёрточные типы, а не примитивы: «не задано» и «задано нулём» — разные
     * случаи. Событие входа в учётку не знает ни номера партии, ни числа слов,
     * и подставлять им нули значило бы записать в отчёт партию нулевой длины.
     */
    @Column(name = "player_count", updatable = false)
    private Short playerCount;

    @Column(name = "team_count", updatable = false)
    private Short teamCount;

    @Column(name = "word_count", updatable = false)
    private Integer wordCount;

    @Column(name = "game_number", updatable = false)
    private Integer gameNumber;

    @Column(name = "duration_seconds", updatable = false)
    private Integer durationSeconds;

    @Builder.Default
    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt = Instant.now();
}
