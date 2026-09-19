package ru.hothat.profile.usecase;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import ru.hothat.config.ApiException;
import ru.hothat.profile.api.dto.DivisionLanguage;
import ru.hothat.profile.api.dto.PlayerRankingView;
import ru.hothat.profile.api.dto.PlayerRankingsView;
import ru.hothat.profile.api.dto.PublicPlayerResponseDTO;
import ru.hothat.profile.api.dto.RankedTeamView;
import ru.hothat.profile.store.ProfileStore;
import ru.hothat.rating.spi.SeasonStandingPort;
import ru.hothat.team.spi.RankedTeamPort;
import ru.hothat.util.Divisions;

import java.util.List;

/**
 * Показать публичную карточку чужого игрока.
 *
 * <p>Наружу едут ник, аватар, дивизион, команда и рейтинги сезона — и ничего
 * больше: ни почты, ни присутствия, ни служебных полей учётной записи.
 * Несуществующий игрок отвечает 404 {@code PLAYER_NOT_FOUND}.
 *
 * <p>Команда и её состав приходят из области команды, показатели сезона — из
 * области рейтинга. Своих копий ника и аватара у них больше нет, поэтому
 * подставляются они здесь, из карточки: раньше строка рейтинга показывала имя
 * на момент последней зачтённой партии, то есть до конца сезона могла врать.
 *
 * <p>Ников участников команды здесь нет намеренно: карточка чужого игрока
 * показывает имя команды и ссылку на неё, а перечислять её состав по именам —
 * работа экрана команды.
 */
@Service
@RequiredArgsConstructor
public class GetPublicPlayerUseCase {

    /** Два режима сезона; порядок совпадает с формой ответа. */
    private static final String CLASSIC = "classic";
    private static final String SABOTAGE = "sabotage";

    private final ProfileStore profiles;
    private final RankedTeamPort rankedTeams;
    private final SeasonStandingPort standings;

    @PreAuthorize("hasRole('USER')")
    public PublicPlayerResponseDTO run(String uid) {
        String clean = uid == null ? "" : uid.trim();
        ProfileStore.Card card = profiles.card(clean)
                .orElseThrow(() -> ApiException.of("PLAYER_NOT_FOUND", 404));
        String division = Divisions.normalize(card.divisionLanguage());

        RankedTeamPort.TeamSummary summary = rankedTeams.teamOf(clean).orElse(null);
        RankedTeamView team = summary == null ? null : new RankedTeamView(
                summary.teamId(),
                summary.name(),
                emptyToNull(rankedTeams.logos(List.of(summary.teamId())).get(summary.teamId())),
                DivisionLanguage.fromWire(Divisions.normalize(summary.divisionLanguage(), division)),
                summary.memberUids(),
                List.of());

        return new PublicPlayerResponseDTO(
                clean,
                card.nickname(),
                emptyToNull(card.avatarDataUrl()),
                DivisionLanguage.fromWire(division),
                Divisions.badge(division, team != null),
                team,
                new PlayerRankingsView(
                        ranking(card, division, summary, CLASSIC),
                        ranking(card, division, summary, SABOTAGE)));
    }

    /**
     * Строка личного рейтинга. Ноль игр и ноль очков — это «ещё не играл», и
     * ответом здесь остаётся {@code null}, а не строка из нулей: экран на это
     * рассчитывает и рисует в этом месте приглашение сыграть.
     */
    private PlayerRankingView ranking(ProfileStore.Card card, String division,
                                      RankedTeamPort.TeamSummary team, String mode) {
        SeasonStandingPort.PlayerStanding row = standings.currentSeasonPlayer(card.uid(), mode, division);
        if (row.games() == 0 && row.points() == 0) {
            return null;
        }
        return new PlayerRankingView(
                card.uid(),
                card.nickname(),
                emptyToNull(card.avatarDataUrl()),
                DivisionLanguage.fromWire(division),
                row.teamId(),
                team == null ? null : team.name(),
                row.points(),
                row.games(),
                row.wins());
    }

    private static String emptyToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
