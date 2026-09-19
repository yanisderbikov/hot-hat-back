package ru.hothat.model.ops;

import io.hypersistence.utils.hibernate.type.json.JsonType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Type;

import java.time.Instant;
import java.util.Map;

/** Одно письмо на метрику и период: id = period_metric_50. */
@Entity
@Table(name = "usage_alert")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UsageAlert {

    @Id
    @Column(nullable = false, length = 200)
    private String id;

    @Type(JsonType.class)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> metric;

    @Builder.Default
    @Column(nullable = false)
    private Integer threshold = 50;

    @Builder.Default
    @Column(nullable = false, length = 24)
    private String status = "pending";

    @Type(JsonType.class)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> email;

    @Builder.Default
    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at")
    private Instant updatedAt;
}
