package ru.hothat.common.identity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

/**
 * Строка моста: какому старому идентификатору соответствует новый uuid.
 *
 * <p>Класс не публичный: наружу мост отдаёт только строки и uuid через
 * {@link LegacyIdBridge}. Сущность не появляется ни в сигнатурах сценариев,
 * ни в DTO — то же правило, что у {@code auth.store.UserBan}.
 */
@Entity
@Table(name = "legacy_id_bridge", schema = "v2")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
class LegacyIdLink {

    @Id
    @Column(name = "v2_id", nullable = false, updatable = false)
    private UUID v2Id;

    @Column(nullable = false, length = 16, updatable = false)
    private String kind;

    @Column(name = "legacy_id", nullable = false, length = 180, updatable = false)
    private String legacyId;
}
