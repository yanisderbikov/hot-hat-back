package ru.hothat.team.store;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Дверь в {@code v2.ranked_team}; за пределы области команды не выходит.
 *
 * <p>Отдельной таблицы имён больше нет: {@code name_key} — вычисляемая колонка
 * ({@code lower(btrim(name))}) с уникальным индексом, поэтому «занято ли имя»
 * спрашивается у самой команды и разойтись с ней не может.
 */
@Repository
interface Teams extends JpaRepository<Team, UUID> {

    boolean existsByNameKey(String nameKey);

    /** Пачкой: таблица рейтинга рисует сотню строк и на каждой называет команду. */
    List<Team> findByIdIn(Collection<UUID> ids);
}
