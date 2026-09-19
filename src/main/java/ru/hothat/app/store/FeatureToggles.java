package ru.hothat.app.store;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Переключатели возможностей в таблице {@code v2.feature_flag}.
 *
 * <p>Не публичный: дверь области наружу одна — {@link FeatureFlagStore}.
 * Сущность {@link FeatureToggle} за пределы {@code app.store} не выходит,
 * иначе право переключить флаг оказалось бы у каждого, кто его читает.
 */
@Repository
interface FeatureToggles extends JpaRepository<FeatureToggle, String> {
}
