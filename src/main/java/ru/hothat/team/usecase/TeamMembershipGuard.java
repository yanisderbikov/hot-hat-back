package ru.hothat.team.usecase;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import ru.hothat.config.ApiException;
import ru.hothat.config.HotHatUser;
import ru.hothat.profile.spi.PlayerCardPort;
import ru.hothat.team.store.TeamStore;
import ru.hothat.util.Divisions;

/**
 * Право распоряжаться командой принадлежит её участнику.
 *
 * <p>Проверка вынесена в отдельное правило, потому что она нужна семи
 * сценариям сразу — лобби, четырём шагам префлайта и чтениям, — и повторять
 * её в каждом значило бы завести семь мест, которые со временем разойдутся.
 *
 * <p>В плане это предикат {@code @teamAuthz.isActiveMember} из
 * {@code ru.hothat.security.authz}. Пакета безопасности ещё нет, а заводить
 * его из области команды нельзя, поэтому предусловие стоит внутри сценария —
 * там же, где стоит проверка друга у переписки.
 *
 * <p>«Участник» здесь строже, чем «числится в составе»: команда должна быть
 * подтверждена напарником, а дивизион игрока — совпадать с дивизионом команды.
 * Ответ на вопрос «в какой я команде» теперь один — строка состава; раньше его
 * давали два места сразу ({@code ranked_team.member_uids} и
 * {@code app_user.ranked_team_id}), и при расхождении игрок видел команду,
 * которая его не помнит.
 */
@Component("teamAuthz")
@RequiredArgsConstructor
public class TeamMembershipGuard {

    private final TeamStore teams;
    /** Дивизион игрока — у карточки: своей копии у команды нет. */
    private final PlayerCardPort cards;

    /**
     * Подтверждённая команда текущего игрока.
     *
     * <p>Коды отказа те же, что были у старого движка, — экраны их уже
     * разбирают: 409 {@code RANKED_TEAM_REQUIRED} — команды нет или она не
     * подтверждена, 409 {@code TEAM_DIVISION_MISMATCH} — игрок сменил дивизион
     * и вышел из лиги своей команды.
     */
    public TeamStore.TeamRow requireActiveTeam(HotHatUser user) {
        return requireActiveTeam(user, cards.card(user.uid()).orElse(null));
    }

    /**
     * То же самое, когда профиль уже прочитан.
     *
     * <p>Отдельная перегрузка ради одного: сценарии префлайта поднимают
     * карточку игрока и сами, и читать её второй раз только ради дивизиона
     * незачем. {@code null} — карточки нет: тогда дивизион считается
     * запасным, и пара с настоящим дивизионом её не примет.
     */
    public TeamStore.TeamRow requireActiveTeam(HotHatUser user, PlayerCardPort.Card card) {
        TeamStore.TeamRow team = teams.teamOf(user.uid())
                .orElseThrow(() -> ApiException.of("RANKED_TEAM_REQUIRED", 409));
        if (!team.isActive()) {
            throw ApiException.of("RANKED_TEAM_REQUIRED", 409);
        }
        if (!Divisions.normalize(team.divisionLanguage())
                .equals(Divisions.normalize(card == null ? null : card.divisionLanguage()))) {
            throw ApiException.of("TEAM_DIVISION_MISMATCH", 409);
        }
        // Пара — это ровно двое. Команда с одним участником в подтверждённом
        // состоянии существовать не может, но чинить её здесь нечем, а пускать
        // такую в рейтинговую партию нельзя.
        if (team.members().size() != 2 || !team.hasMember(user.uid())) {
            throw ApiException.of("RANKED_TEAM_INVALID", 409);
        }
        return team;
    }
}
