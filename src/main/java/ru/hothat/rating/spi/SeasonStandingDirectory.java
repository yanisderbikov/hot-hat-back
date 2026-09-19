package ru.hothat.rating.spi;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import ru.hothat.rating.store.RatingStore;
import ru.hothat.util.Divisions;
import ru.hothat.util.Seasons;

import java.util.List;

/**
 * Реализация read-порта рейтинга: живёт у владельца данных, как велит §7.3
 * плана.
 *
 * <p>Доска здесь только читается и никогда не заводится: спрашивают её те, кто
 * ничего не начисляет, и создавать таблицу сезона от одного взгляда на
 * карточку команды незачем.
 */
@Component
@RequiredArgsConstructor
public class SeasonStandingDirectory implements SeasonStandingPort {

    private final RatingStore ratings;

    @Override
    public TeamStanding currentSeason(String teamId, String mode, String divisionLanguage) {
        if (teamId == null || teamId.isBlank()) {
            return TeamStanding.EMPTY;
        }
        Seasons.Season season = Seasons.now();
        return ratings.board(season.year(), season.season(), mode, Divisions.normalize(divisionLanguage))
                .flatMap(board -> ratings.teamStandings(board.boardId(), List.of(teamId))
                        .values().stream().findFirst())
                .map(row -> new TeamStanding(row.points(), row.games(), row.wins(), row.technicalForfeits()))
                .orElse(TeamStanding.EMPTY);
    }

    @Override
    public PlayerStanding currentSeasonPlayer(String uid, String mode, String divisionLanguage) {
        if (uid == null || uid.isBlank()) {
            return PlayerStanding.EMPTY;
        }
        Seasons.Season season = Seasons.now();
        return ratings.board(season.year(), season.season(), mode, Divisions.normalize(divisionLanguage))
                .flatMap(board -> ratings.playerStanding(board.boardId(), uid))
                .map(row -> new PlayerStanding(row.points(), row.games(), row.wins(), row.teamId()))
                .orElse(PlayerStanding.EMPTY);
    }
}
