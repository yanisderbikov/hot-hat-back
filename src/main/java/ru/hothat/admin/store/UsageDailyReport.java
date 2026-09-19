package ru.hothat.admin.store;

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
import java.time.LocalDate;

/**
 * Ежедневный отчёт о расходе.
 *
 * <p>Имя класса не {@code UsageReport}: так зовётся легаси-сущность.
 *
 * <p>Сегодня в строке отчёта лежит jsonb-КОПИЯ всего снимка. Копия устаревает
 * в тот же миг, когда снимок пересобирают, и весит столько же, сколько сам
 * снимок. Здесь вместо копии ссылка {@link #snapshotId}.
 *
 * <p>Ключ — день отчёта, и он же правило «один отчёт в сутки»: сегодня это
 * держится тем, что строку кладут по ключу-дате, но дата там строка, а не дата.
 */
@Entity
@Table(name = "usage_report", schema = "v2")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
class UsageDailyReport {

    /** Стадия отчёта; набор закрыт ограничением базы. */
    static final String PENDING = "pending";
    static final String SENT = "sent";
    static final String FAILED = "failed";

    @Id
    @Column(name = "report_date", nullable = false, updatable = false)
    private LocalDate reportDate;

    /** Пусто, если отчёт готовили, а снимок снять не удалось. */
    @Column(name = "snapshot_id")
    private Long snapshotId;

    @Column(name = "email_id")
    private Long emailId;

    @Builder.Default
    @Column(name = "time_zone", nullable = false, length = 60)
    private String timeZone = "UTC";

    @Builder.Default
    @Column(nullable = false, length = 16)
    private String status = PENDING;

    @Builder.Default
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at")
    private Instant updatedAt;
}
