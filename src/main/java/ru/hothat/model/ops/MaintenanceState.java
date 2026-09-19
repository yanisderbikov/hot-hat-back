package ru.hothat.model.ops;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/** maintenance/roomCleanup: кулдаун полной уборки комнат. */
@Entity
@Table(name = "maintenance_state")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MaintenanceState {

    @Id
    @Column(nullable = false, length = 60)
    private String id;

    @Builder.Default
    @Column(name = "last_run_at", nullable = false)
    private Long lastRunAt = 0L;

    @Builder.Default
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();
}
