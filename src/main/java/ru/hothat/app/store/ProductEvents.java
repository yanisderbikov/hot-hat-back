package ru.hothat.app.store;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

/**
 * Продуктовые события в таблице {@code v2.analytics_event}.
 *
 * <p>Не публичный: наружу область смотрит через {@link AnalyticsStore} и порт
 * {@code app.spi.ProductEventPort}.
 *
 * <p>Отбор за период идёт с пределом, а не целиком: события приезжают в кучу
 * в память, и запрос за год без предела положил бы процесс. Предел выражен
 * {@link Limit}, а не {@code Pageable}: страницы здесь нет — есть потолок
 * просмотра, и он входит в ответ отдельным признаком {@code truncated}.
 */
@Repository
interface ProductEvents extends JpaRepository<ProductEvent, Long> {

    boolean existsByEventKey(String eventKey);

    List<ProductEvent> findByOccurredAtBetweenOrderByOccurredAtAsc(Instant from, Instant to, Limit limit);
}
