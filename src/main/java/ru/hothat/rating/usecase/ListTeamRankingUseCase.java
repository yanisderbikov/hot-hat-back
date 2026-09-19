package ru.hothat.rating.usecase;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.hothat.config.HotHatUser;
import ru.hothat.friend.spi.FriendshipPort;
import ru.hothat.rating.api.dto.SeasonSelectionView;
import ru.hothat.rating.api.dto.TeamRankingPageResponseDTO;
import ru.hothat.rating.api.dto.TeamRankingQueryDTO;
import ru.hothat.rating.api.dto.TeamStandingView;
import ru.hothat.rating.domain.SeasonSelection;
import ru.hothat.rating.store.RatingStore;
import ru.hothat.team.spi.RankedTeamPort;
import ru.hothat.util.Seasons;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Показать таблицу команд за сезон.
 *
 * <p>Имя, логотип и состав приходят из области команды, а не из самой строки
 * рейтинга: копий этих полей в v2 нет. Раньше они лежали в
 * {@code season_ranking_team} и обновлялись только при зачёте следующей
 * партии — команда меняла имя, а таблица показывала прежнее до конца сезона.
 *
 * <p>Личность нужна не для прав, а для признака {@code hasFriends}: строка
 * помечается, если в составе есть друг спрашивающего. Раньше рядом со списком
 * ехал отдельный массив {@code friendTeamIds}, и клиент пересекал два списка
 * руками.
 *
 * <p>Четыре чтения на всю страницу, сколько бы строк в ней ни было: таблица,
 * команды с составами, ники участников и список друзей.
 */
@Service
@RequiredArgsConstructor
public class ListTeamRankingUseCase {

    private final RatingStore ratings;
    private final RankedTeamPort teams;
    private final RatingProfileDirectory profileDirectory;
    private final FriendshipPort friendships;

    @PreAuthorize("hasRole('USER')")
    @Transactional(readOnly = true)
    public TeamRankingPageResponseDTO run(HotHatUser user, TeamRankingQueryDTO query) {
        Seasons.Season current = Seasons.now();
        SeasonSelection selection = SeasonSelection.resolve(
                query.season(), query.year(), query.mode(), query.division(),
                current.year(), current.season());
        int limit = query.limitOrDefault();
        SeasonSelectionView selectionView = view(selection);

        List<RatingStore.TeamStandingRow> rows = ratings
                .board(selection.year(), selection.season(), selection.mode(), selection.divisionLanguage())
                .map(board -> ratings.teamStandings(board.boardId(), limit))
                .orElse(List.of());
        if (rows.isEmpty()) {
            // Доски может не быть вовсе: сезон ещё не начинался. Это пустая
            // таблица, а не ошибка, и заводить строку от одного взгляда
            // на выпадающий список незачем.
            return new TeamRankingPageResponseDTO(selectionView, List.of(), null, limit);
        }

        List<String> teamIds = new ArrayList<>(rows.size());
        for (RatingStore.TeamStandingRow row : rows) {
            teamIds.add(row.teamId());
        }
        Map<String, RankedTeamPort.TeamSummary> cards = teams.teams(teamIds);
        Map<String, String> logos = teams.logos(teamIds);

        List<String> members = new ArrayList<>();
        for (RankedTeamPort.TeamSummary card : cards.values()) {
            members.addAll(card.memberUids());
        }
        RatingProfileDirectory.Snapshot profiles = profileDirectory.load(members);
        Set<String> friendUids = new HashSet<>(friendships.friendUids(user.uid(), RatingReadLimits.FRIENDS_SCAN));

        List<TeamStandingView> items = new ArrayList<>(rows.size());
        for (int i = 0; i < rows.size(); i++) {
            RatingStore.TeamStandingRow row = rows.get(i);
            RankedTeamPort.TeamSummary card = cards.get(row.teamId());
            List<String> memberUids = card == null ? List.of() : card.memberUids();
            List<String> nicknames = new ArrayList<>(memberUids.size());
            boolean hasFriends = false;
            for (String uid : memberUids) {
                nicknames.add(profiles.nickname(uid));
                hasFriends |= friendUids.contains(uid);
            }
            items.add(new TeamStandingView(
                    // Место считает сервер: у клиента список может быть
                    // отфильтрован, и нумеровать по порядку показа нельзя.
                    i + 1,
                    row.teamId(),
                    card == null ? null : card.name(),
                    logos.get(row.teamId()),
                    selection.divisionLanguage(),
                    memberUids,
                    nicknames,
                    row.points(),
                    row.games(),
                    row.wins(),
                    row.technicalForfeits(),
                    hasFriends));
        }
        return new TeamRankingPageResponseDTO(selectionView, items, null, limit);
    }

    /** Что сервер в итоге открыл: умолчания разобраны {@link SeasonSelection}. */
    private static SeasonSelectionView view(SeasonSelection selection) {
        return new SeasonSelectionView(selection.year(), selection.season(),
                selection.mode(), selection.divisionLanguage());
    }
}
