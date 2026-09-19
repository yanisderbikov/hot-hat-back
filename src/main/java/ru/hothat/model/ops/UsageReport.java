package ru.hothat.model.ops;

import io.hypersistence.utils.hibernate.type.json.JsonType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Type;

import java.time.Instant;
import java.util.Map;

@Entity
@Table(name = "usage_report")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UsageReport {

    @Id
    @Column(nullable = false, length = 10)
    private String date;

    @Column(name = "time_zone", length = 60)
    private String timeZone;

    @Builder.Default
    @Column(nullable = false, length = 24)
    private String status = "pending";

    @Type(JsonType.class)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> snapshot;

    @Type(JsonType.class)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> email;

    @Builder.Default
    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at")
    private Instant updatedAt;
}
