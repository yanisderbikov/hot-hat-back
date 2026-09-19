package ru.hothat.machine.store;

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

/**
 * Аренда на прогон уборки.
 *
 * <p>Заменяет {@code maintenance_state}, у которой была одна колонка
 * {@code last_run_at} и одно правило: «если прошлый прогон был меньше пяти
 * минут назад — не начинать». Правило читало значение, решало и писало обратно
 * тремя отдельными шагами, то есть два планировщика, запущенные одновременно,
 * проходили его оба.
 *
 * <p>{@link #leasedUntil} вместо отметки прошлого запуска: аренда БЕРЁТСЯ на
 * время, а не выводится из «когда начинали». Разница видна, когда прогон падает
 * посреди работы: отметка «начал» осталась бы вечной и заперла бы уборку до
 * ручного вмешательства, а аренда просто истекает.
 *
 * <p>{@code @Version} (§6.4) делает захват аренды одним условным обновлением:
 * проигравший получает 409 и не начинает второй проход.
 */
@Entity
@Table(name = "maintenance_lease", schema = "v2")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
class MaintenanceLease {

    /** Виды уборки; набор закрыт ограничением базы. */
    static final String ROOM_SWEEP = "room_sweep";
    static final String RECORDING_SWEEP = "recording_sweep";
    static final String TOKEN_SWEEP = "token_sweep";

    @Id
    @Column(nullable = false, updatable = false, length = 40)
    private String job;

    @Column(name = "leased_until")
    private Instant leasedUntil;

    /** Кто держит аренду: имя узла или «cron». Это не учётка, ключа нет. */
    @Column(name = "leased_by", length = 80)
    private String leasedBy;

    @Column(name = "last_run_at")
    private Instant lastRunAt;

    @Version
    @Builder.Default
    @Column(nullable = false)
    private Long version = 0L;
}
