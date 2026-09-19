package ru.hothat.model.ops;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/** Счётчик отправленных писем: ключ — дата (YYYY-MM-DD) или месяц (YYYY-MM). */
@Entity
@Table(name = "usage_mail_counter")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UsageMailCounter {

    @Id
    @Column(name = "period_key", nullable = false, length = 16)
    private String periodKey;

    @Builder.Default
    @Column(nullable = false)
    private Integer sent = 0;

    @Builder.Default
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();
}
