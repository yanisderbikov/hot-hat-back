package ru.hothat.team.usecase;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.hothat.config.HotHatUser;
import ru.hothat.profile.spi.PlayerCardPort;
import ru.hothat.profile.api.dto.DivisionLanguage;
import ru.hothat.team.api.dto.MyTeamResponseDTO;
import ru.hothat.team.store.TeamStore;
import ru.hothat.util.Divisions;

/**
 * Показать свою команду: состав, напарника и показатели сезона.
 *
 * <p>Права — {@code hasRole('USER')}, а не «участник команды», хотя в плане у
 * этого адреса стоит предикат участника. Причина в том, что именно этим
 * запросом экран команды и выясняет, есть ли она: игроку без команды он
 * показывает форму основания. Ответ 403 на вопрос «есть ли у меня команда»
 * закрыл бы единственную дорогу к её созданию, поэтому отсутствие команды
 * здесь — нормальный ответ с пустыми полями, а не отказ.
 *
 * <p>Команда ищется по строке состава, а не по колонке в карточке игрока:
 * у принадлежности к команде теперь один хозяин, и «числюсь, но команда меня
 * не помнит» стало невозможным состоянием.
 *
 * <p>Ни приглашений, ни снимка префлайта в ответе нет: у каждого из них свой
 * адрес. Старый {@code my_team} собирал все три предмета разом, и страница
 * портала перезапрашивала его целиком раз в полторы секунды ради флагов
 * готовности — вместе со списком приглашений и статистикой сезона.
 */
@Service
@RequiredArgsConstructor
public class GetMyTeamUseCase {

    /** Дивизион и значок берутся у карточки игрока, а не у оболочки учётки. */
    private final PlayerCardPort cards;
    private final TeamStore teams;
    private final TeamProfileDirectory profileDirectory;
    private final TeamCardAssembler cardAssembler;
    private final TeamSeasonStatsReader statsReader;

    @PreAuthorize("hasRole('USER')")
    @Transactional
    public MyTeamResponseDTO run(HotHatUser user) {
        String divisionLanguage = Divisions.normalize(cards.card(user.uid())
                .map(PlayerCardPort.Card::divisionLanguage).orElse(null));
        DivisionLanguage division = DivisionLanguage.fromWire(divisionLanguage);

        TeamStore.TeamRow team = teams.teamOf(user.uid()).orElse(null);
        if (team == null) {
            // Значок дивизиона считает сервер и без команды: без неё это
            // название языка, с ней — название дивизиона, и правило одно.
            return new MyTeamResponseDTO(null, null, null, division,
                    Divisions.badge(divisionLanguage, false));
        }

        // Профили обоих участников — одной пачкой. Раньше здесь стоял цикл с
        // чтением профиля на каждого, и тот же цикл повторялся ради аватаров.
        TeamProfileDirectory.Snapshot profiles = profileDirectory.load(team.memberUids());
        return new MyTeamResponseDTO(
                cardAssembler.card(team, teams.logo(team.teamId()).orElse(null), profiles),
                cardAssembler.partner(team, user.uid(), profiles),
                statsReader.read(team.teamId(), team.divisionLanguage()),
                division,
                Divisions.badge(divisionLanguage, true));
    }
}
