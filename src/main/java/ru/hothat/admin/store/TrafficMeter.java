package ru.hothat.admin.store;

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
 * Последнее сырое показание счётчика ОС.
 *
 * <p>Заменяет {@code usage_monitor_state}. Месячный трафик считается ДЕЛЬТАМИ,
 * потому что счётчик {@code /proc} обнуляется при перезагрузке машины: строка
 * нужна затем, чтобы знать, от чего вычитать.
 *
 * <p>{@code @Version} (§6.4): сбор снимка — честный read-modify-write, и два
 * одновременных сбора (плановый и «Проверить сейчас») удвоили бы дельту, то
 * есть нарисовали бы вдвое больший расход трафика.
 */
@Entity
@Table(name = "usage_traffic_meter", schema = "v2")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
class TrafficMeter {

    @Id
    @Column(name = "meter_id", nullable = false, updatable = false, length = 40)
    private String meterId;

    @Column(name = "tx_bytes")
    private Long txBytes;

    @Column(name = "rx_bytes")
    private Long rxBytes;

    @Builder.Default
    @Column(name = "read_at", nullable = false)
    private Instant readAt = Instant.now();

    @Version
    @Builder.Default
    @Column(nullable = false)
    private Long version = 0L;
}
