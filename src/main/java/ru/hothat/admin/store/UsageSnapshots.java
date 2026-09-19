package ru.hothat.admin.store;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Снимки расхода в таблице {@code v2.usage_snapshot}.
 *
 * <p>Не публичный: дверь области наружу одна — {@link UsageStore}.
 *
 * <p>Снимков за день теперь несколько — их снимают четыре раза в сутки, — и
 * оба запроса упорядочены по времени, а не по дате. Раньше строка была одна на
 * день, и три снимка из четырёх затирали друг друга молча.
 */
@Repository
interface UsageSnapshots extends JpaRepository<UsageSnapshot, Long> {

    /** Страница истории: свежие сверху, предел задаёт вызывающий. */
    List<UsageSnapshot> findByLocalDateBetweenOrderByTakenAtDesc(LocalDate from, LocalDate to, Pageable page);

    /** Самый свежий снимок независимо от диапазона на экране. */
    Optional<UsageSnapshot> findFirstByOrderByTakenAtDesc();
}
