package ru.hothat.model.ops;

import io.hypersistence.utils.hibernate.type.json.JsonType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Type;

import java.time.Instant;
import java.util.Map;

@Entity
@Table(name = "usage_daily")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UsageDaily {

    @Id
    @Column(nullable = false, length = 10)
    private String date;

    @Type(JsonType.class)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> latest;

    @Builder.Default
    @Column(name = "vps_network_tx_bytes", nullable = false)
    private Long vpsNetworkTxBytes = 0L;

    @Builder.Default
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();
}
