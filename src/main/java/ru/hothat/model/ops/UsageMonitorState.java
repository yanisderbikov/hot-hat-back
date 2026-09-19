package ru.hothat.model.ops;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/** Предыдущие показания счётчиков VPS — из них считается дельта трафика. */
@Entity
@Table(name = "usage_monitor_state")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UsageMonitorState {

    @Id
    @Column(nullable = false, length = 60)
    private String id;

    @Column(name = "tx_bytes")
    private Long txBytes;

    @Column(name = "rx_bytes")
    private Long rxBytes;

    @Builder.Default
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();
}
