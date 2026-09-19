package ru.hothat.rating.usecase;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.hothat.config.HotHatUser;
import ru.hothat.rating.api.dto.SeasonChampionQueryDTO;
import ru.hothat.rating.api.dto.SeasonChampionResponseDTO;
import ru.hothat.rating.api.dto.SeasonChampionView;
import ru.hothat.rating.api.dto.SeasonSelectionView;
import ru.hothat.rating.domain.SeasonSelection;
import ru.hothat.rating.store.RatingStore;
import ru.hothat.team.spi.RankedTeamPort;
import ru.hothat.util.Seasons;

import java.util.List;

/**
 * Показать чемпиона сезона.
 *
 * <p>Одна строка вместо двух таблиц. Раньше за именем чемпиона в шапке
 * страницы поднимались сотня команд и сотня игроков: чемпион был полем в
 * общем ответе, а не собственным адресом.
 *
 * <p>Чемпион хранится снимком — имя и идентификатор команды прямо в доске.
 * Сезон закончился, команда могла распасться, а «победитель 2026-winter»
 * обязан пережить её роспуск. Логотип при этом снимком не хранится: он весит
 * до 280 КБ, и берётся у команды, пока она существует.
 */
@Service
@RequiredArgsConstructor
public class GetSeasonChampionUseCase {

    private final RatingStore ratings;
    private final RankedTeamPort teams;

    @PreAuthorize("hasRole('USER')")
    @Transactional(readOnly = true)
    public SeasonChampionResponseDTO run(HotHatUser user, SeasonChampionQueryDTO query) {
        Seasons.Season current = Seasons.now();
        SeasonSelection selection = SeasonSelection.resolve(
                query.season(), query.year(), query.mode(), query.division(),
                current.year(), current.season());
        SeasonSelectionView selectionView = new SeasonSelectionView(selection.year(), selection.season(),
                selection.mode(), selection.divisionLanguage());

        RatingStore.BoardRow board = ratings
                .board(selection.year(), selection.season(), selection.mode(), selection.divisionLanguage())
                .orElse(null);
        // Доска без имени чемпиона равносильна его отсутствию: показывать
        // «Победитель сезона — «»» хуже, чем не показывать ничего.
        if (board == null || board.championName() == null || board.championName().isBlank()) {
            return new SeasonChampionResponseDTO(selectionView, null);
        }
        String logo = board.championTeamId() == null
                ? null
                : teams.logos(List.of(board.championTeamId())).get(board.championTeamId());
        return new SeasonChampionResponseDTO(selectionView,
                new SeasonChampionView(board.championTeamId(), board.championName(), logo));
    }
}
