package ru.hothat.team.usecase;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import ru.hothat.support.FakePlayerCards;
import ru.hothat.support.Users;
import ru.hothat.team.store.FakeTeamStore;
import ru.hothat.team.store.TeamStore;

import static org.assertj.core.api.Assertions.assertThat;
import static ru.hothat.support.Refusals.refuses;

/**
 * Право распоряжаться командой принадлежит её подтверждённому участнику.
 *
 * <p>«Участник» здесь строже, чем «числится в составе»: пара должна быть
 * подтверждена напарником, а дивизион игрока — совпадать с дивизионом команды.
 */
class TeamMembershipGuardTest {

    @Test
    @DisplayName("подтверждённая пара своего дивизиона проходит и возвращается целиком")
    void activeTeamPasses() {
        TeamStore.TeamRow team = guard(FakeTeamStore.of("team-red", TeamStore.ACTIVE, "ru", Users.ANN, Users.BOB),
                FakePlayerCards.empty().card(Users.ANN, "ru"))
                .requireActiveTeam(Users.player(Users.ANN));
        assertThat(team.memberUids()).containsExactly(Users.ANN, Users.BOB);
    }

    @Test
    @DisplayName("без команды в рейтинг не пускают")
    void noTeamIsRefused() {
        TeamMembershipGuard guard = guard(FakeTeamStore.empty(),
                FakePlayerCards.empty().card(Users.ANN, "ru"));
        refuses("RANKED_TEAM_REQUIRED", 409, () -> guard.requireActiveTeam(Users.player(Users.ANN)));
    }

    @Test
    @DisplayName("неподтверждённая пара — ещё не команда")
    void pendingTeamIsRefused() {
        TeamMembershipGuard guard = guard(
                FakeTeamStore.of("team-red", TeamStore.PENDING, "ru", Users.ANN, Users.BOB),
                FakePlayerCards.empty().card(Users.ANN, "ru"));
        refuses("RANKED_TEAM_REQUIRED", 409, () -> guard.requireActiveTeam(Users.player(Users.ANN)));
    }

    @Test
    @DisplayName("сменивший дивизион вышел из лиги своей команды")
    void divisionMismatchIsRefused() {
        TeamMembershipGuard guard = guard(
                FakeTeamStore.of("team-red", TeamStore.ACTIVE, "ru", Users.ANN, Users.BOB),
                FakePlayerCards.empty().card(Users.ANN, "de"));
        refuses("TEAM_DIVISION_MISMATCH", 409, () -> guard.requireActiveTeam(Users.player(Users.ANN)));
    }

    @Test
    @DisplayName("игрок без карточки считается игроком запасного дивизиона")
    void missingCardFallsBackToDefaultDivision() {
        // Пара с настоящим дивизионом такого не примет, а «ru» — примет:
        // запасное значение здесь именно оно.
        refuses("TEAM_DIVISION_MISMATCH", 409, () -> guard(
                FakeTeamStore.of("team-red", TeamStore.ACTIVE, "de", Users.ANN, Users.BOB),
                FakePlayerCards.empty()).requireActiveTeam(Users.player(Users.ANN)));
        assertThat(guard(FakeTeamStore.of("team-red", TeamStore.ACTIVE, "ru", Users.ANN, Users.BOB),
                FakePlayerCards.empty()).requireActiveTeam(Users.player(Users.ANN)).teamId())
                .isEqualTo("team-red");
    }

    @Test
    @DisplayName("пара — это ровно двое: команда из одного в рейтинговую партию не идёт")
    void loneMemberIsRefused() {
        TeamMembershipGuard guard = guard(
                FakeTeamStore.of("team-red", TeamStore.ACTIVE, "ru", Users.ANN),
                FakePlayerCards.empty().card(Users.ANN, "ru"));
        refuses("RANKED_TEAM_INVALID", 409, () -> guard.requireActiveTeam(Users.player(Users.ANN)));
    }

    @Test
    @DisplayName("чужой командой распоряжаться нельзя, даже найдя её по своему uid")
    void teamWithoutMeIsRefused() {
        // Состав отвечает «эта», а самого спрашивающего в нём нет: расхождение
        // двух источников принадлежности, ради которого и стоит проверка.
        TeamMembershipGuard guard = guard(
                FakeTeamStore.of("team-red", TeamStore.ACTIVE, "ru", Users.BOB, "uid-cat"),
                FakePlayerCards.empty().card(Users.MALLORY, "ru"));
        refuses("RANKED_TEAM_INVALID", 409, () -> guard.requireActiveTeam(Users.player(Users.MALLORY)));
    }

    private static TeamMembershipGuard guard(FakeTeamStore teams, FakePlayerCards cards) {
        return new TeamMembershipGuard(teams, cards);
    }
}
