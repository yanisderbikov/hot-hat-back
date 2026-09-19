package ru.hothat.team.store;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Дверь в {@code v2.ranked_team_logo}; за пределы области команды не выходит.
 *
 * <p>Отдельная таблица — чтобы 280 КБ логотипа не ехали вместе с карточкой
 * туда, где её рисуют строкой списка. Поэтому и чтение здесь всегда явное:
 * логотип просит тот экран, который его показывает.
 */
@Repository
interface TeamLogos extends JpaRepository<TeamLogo, UUID> {

    List<TeamLogo> findByTeamIdIn(Collection<UUID> teamIds);
}
