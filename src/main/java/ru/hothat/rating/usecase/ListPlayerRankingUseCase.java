package ru.hothat.rating.usecase;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.hothat.config.HotHatUser;
import ru.hothat.rating.api.dto.PlayerRankingPageResponseDTO;
import ru.hothat.rating.api.dto.PlayerRankingQueryDTO;
import ru.hothat.rating.api.dto.PlayerStandingView;
import ru.hothat.rating.api.dto.SeasonSelectionView;
import ru.hothat.rating.domain.SeasonSelection;
import ru.hothat.rating.store.RatingStore;
import ru.hothat.team.spi.RankedTeamPort;
import ru.hothat.util.Seasons;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Показать таблицу игроков за сезон.
 *
 * <p>Ник, аватар и название команды приходят из своих областей: в строке
 * личного рейтинга их копий больше нет. Именно они устаревали дольше всего —
 * строка обновлялась только при зачёте следующей партии игрока.
 *
 * <p>Личный список больше не достраивается из командных очков на лету, как
 * это делал старый движок при пустой таблице: строка игрока пишется тем же
 * зачётом, что и командная, и «таблица ещё не наполнилась» стало невозможным
 * состоянием.
 *
 * <p>Три чтения на всю страницу: таблица, карточки игроков, названия команд.
 */
@Service
@RequiredArgsConstructor
public class ListPlayerRankingUseCase {

    private final RatingStore ratings;
    private final RankedTeamPort teams;
    private final RatingProfileDirectory profileDirectory;

    @PreAuthorize("hasRole('USER')")
    @Transactional(readOnly = true)
    public PlayerRankingPageResponseDTO run(HotHatUser user, PlayerRankingQueryDTO query) {
        Seasons.Season current = Seasons.now();
        SeasonSelection selection = SeasonSelection.resolve(
                query.season(), query.year(), query.mode(), query.division(),
                current.year(), current.season());
        int limit = query.limitOrDefault();
        SeasonSelectionView selectionView = new SeasonSelectionView(selection.year(), selection.season(),
                selection.mode(), selection.divisionLanguage());

        List<RatingStore.PlayerStandingRow> rows = ratings
                .board(selection.year(), selection.season(), selection.mode(), selection.divisionLanguage())
                .map(board -> ratings.playerStandings(board.boardId(), limit))
                .orElse(List.of());
        if (rows.isEmpty()) {
            return new PlayerRankingPageResponseDTO(selectionView, List.of(), null, limit);
        }

        List<String> uids = new ArrayList<>(rows.size());
        List<String> teamIds = new ArrayList<>();
        for (RatingStore.PlayerStandingRow row : rows) {
            uids.add(row.uid());
            if (row.teamId() != null) {
                teamIds.add(row.teamId());
            }
        }
        RatingProfileDirectory.Snapshot profiles = profileDirectory.load(uids);
        Map<String, RankedTeamPort.TeamSummary> cards = teams.teams(teamIds);

        List<PlayerStandingView> items = new ArrayList<>(rows.size());
        for (int i = 0; i < rows.size(); i++) {
            RatingStore.PlayerStandingRow row = rows.get(i);
            RankedTeamPort.TeamSummary card = row.teamId() == null ? null : cards.get(row.teamId());
            items.add(new PlayerStandingView(
                    i + 1,
                    row.uid(),
                    profiles.nickname(row.uid()),
                    profiles.avatarDataUrl(row.uid()),
                    selection.divisionLanguage(),
                    row.teamId(),
                    // Команда могла распасться после того, как игрок набрал
                    // эти очки: строка сезона переживает роспуск, название —
                    // нет, и врать выдуманным именем нельзя.
                    card == null ? null : card.name(),
                    row.points(),
                    row.games(),
                    row.wins()));
        }
        return new PlayerRankingPageResponseDTO(selectionView, items, null, limit);
    }
}
