package ru.hothat.admin.store;

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

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Тревога о том, что метрика перевалила порог.
 *
 * <p>Имя класса не {@code UsageAlert}: так зовётся легаси-сущность, а имена
 * сущностей у Hibernate общие на всё приложение.
 *
 * <p>Сегодня ключ склеен из трёх значений («2026-09_vps-диск_50»), причём
 * средним куском в него идёт РУССКАЯ ПОДПИСЬ метрики, приведённая к нижнему
 * регистру. Переименование подписи заводит вторую тревогу о том же, и письмо
 * уходит второй раз. Здесь то же правило выражено уникальным индексом по
 * четырём колонкам, и подпись в него не входит.
 *
 * <p>{@link #emailId} вместо jsonb с телом письма: письмо — сущность со своим
 * состоянием, и живёт оно в журнале {@link OutboundEmail}.
 */
@Entity
@Table(name = "usage_alert", schema = "v2")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
class UsageThresholdAlert {

    /** Что стало с письмом; набор закрыт ограничением базы. */
    static final String PENDING = "pending";
    static final String NOTIFIED = "notified";
    static final String FAILED = "failed";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** day | month | total. */
    @Column(name = "period_kind", nullable = false, updatable = false, length = 8)
    private String periodKind;

    @Column(name = "period_key", nullable = false, updatable = false, length = 10)
    private String periodKey;

    @Column(name = "metric_key", nullable = false, updatable = false, length = 60)
    private String metricKey;

    /** Подпись метрики на момент срабатывания — снимок, как и в самой метрике. */
    @Column(name = "metric_label", nullable = false, updatable = false, length = 120)
    private String metricLabel;

    @Builder.Default
    @Column(nullable = false, updatable = false)
    private Short threshold = 50;

    /** Доля израсходованного на момент срабатывания. */
    @Column(updatable = false, precision = 6, scale = 2)
    private BigDecimal percent;

    @Builder.Default
    @Column(nullable = false, length = 16)
    private String status = PENDING;

    @Column(name = "email_id")
    private Long emailId;

    @Builder.Default
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at")
    private Instant updatedAt;
}
