package ru.hothat.app.store;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * Переключатель возможности.
 *
 * <p>Имя класса не {@code FeatureFlag} по той же причине, что у соседа:
 * так зовётся легаси-сущность, а имена сущностей у Hibernate общие.
 *
 * <p>Против сегодняшней строки добавлено ровно одно поле — {@link #updatedBy}.
 * Флаг переключают руками в базе, и «кто включил бота» — единственное, чего
 * сейчас не узнать.
 *
 * <p>Списка допустимых имён в базе нет намеренно: набор закрыт проверкой на
 * входе ({@code FeatureFlagName} и {@code public/features.js}), и заводить
 * новую возможность через миграцию базы было бы дороже, чем она стоит.
 */
@Entity
@Table(name = "feature_flag", schema = "v2")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
class FeatureToggle {

    @Id
    @Column(nullable = false, updatable = false, length = 60)
    private String name;

    @Builder.Default
    @Column(nullable = false)
    private Boolean enabled = false;

    @Column(length = 300)
    private String description;

    @Column(name = "updated_by")
    private UUID updatedBy;

    @Builder.Default
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();
}
