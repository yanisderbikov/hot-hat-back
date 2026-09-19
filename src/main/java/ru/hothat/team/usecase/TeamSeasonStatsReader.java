package ru.hothat.team.usecase;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import ru.hothat.rating.spi.SeasonStandingPort;
import ru.hothat.team.api.dto.GameMode;
import ru.hothat.team.api.dto.TeamSeasonStatsView;
import ru.hothat.team.api.dto.TeamStatsView;
import ru.hothat.util.Divisions;

/**
 * Показатели команды за текущий сезон в обоих режимах.
 *
 * <p>Живёт отдельно, потому что читают их два сценария — своя команда и чужой
 * публичный профиль, — и раньше они собирали одно и то же двумя разными
 * циклами в одном классе, причём с разным набором полей: своей команде движок
 * показывал рейтинг, а чужой — технические поражения.
 *
 * <p>Спрашивает область рейтинга через её порт, а не таблицу: таблицы сезона
 * принадлежат рейтингу, и ходить в них из команды значило бы завести им
 * второго хозяина. Два обращения, а не цикл: режимов ровно два и они закрыты
 * перечислением.
 */
@Component
@RequiredArgsConstructor
public class TeamSeasonStatsReader {

    /**
     * Рейтинг команды. В v2 колонки {@code rating} нет: сегодняшний jsonb
     * {@code {classic:1000, sabotage:1000}} присваивался при основании и не
     * менялся никогда — «вечная тысяча». Поле в контракте осталось, потому
     * что его показывает экран команды, и врать ему нечем: значение и правда
     * одно для всех.
     */
    private static final int ETERNAL_RATING = 1000;

    private final SeasonStandingPort standings;

    public TeamStatsView read(String teamId, String divisionLanguage) {
        String division = Divisions.normalize(divisionLanguage);
        return new TeamStatsView(
                mode(teamId, GameMode.CLASSIC, division),
                mode(teamId, GameMode.SABOTAGE, division));
    }

    private TeamSeasonStatsView mode(String teamId, GameMode mode, String division) {
        SeasonStandingPort.TeamStanding row = standings.currentSeason(teamId, mode.wireValue(), division);
        return new TeamSeasonStatsView(
                mode,
                ETERNAL_RATING,
                row.points(),
                row.games(),
                row.wins(),
                row.technicalForfeits());
    }
}
