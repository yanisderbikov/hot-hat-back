package ru.hothat.model.ops;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "usage_monthly")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UsageMonthly {

    @Id
    @Column(nullable = false, length = 7)
    private String month;

    @Builder.Default
    @Column(name = "vps_network_tx_bytes", nullable = false)
    private Long vpsNetworkTxBytes = 0L;

    @Builder.Default
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();
}
