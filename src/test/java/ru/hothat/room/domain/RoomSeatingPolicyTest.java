package ru.hothat.room.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Составы команд внутри комнаты: сколько их, кто садится и как тасует жеребьёвка. */
class RoomSeatingPolicyTest {

    @Test
    @DisplayName("Заводить команды можно только в наборе")
    void teamsAreCreatedOnlyInSetup() {
        assertThat(RoomSeatingPolicy.refuseTeamCreation(RoomPhase.SETUP, 0, "Соколы", List.of())).isEmpty();
        assertThat(RoomSeatingPolicy.refuseTeamCreation(RoomPhase.ACTIVE, 0, "Соколы", List.of()))
                .contains(RoomSeatingPolicy.Refusal.SETUP_ONLY);
    }

    @Test
    @DisplayName("Больше пяти команд комната не собирает")
    void fiveTeamsIsTheLimit() {
        assertThat(RoomSeatingPolicy.refuseTeamCreation(RoomPhase.SETUP, 4, "Пятая", List.of())).isEmpty();
        assertThat(RoomSeatingPolicy.refuseTeamCreation(RoomPhase.SETUP, 5, "Шестая", List.of()))
                .contains(RoomSeatingPolicy.Refusal.TEAM_LIMIT_REACHED);
    }

    @Test
    @DisplayName("Названия, различимые только регистром, считаются занятыми: на экране они одинаковы")
    void namesAreComparedIgnoringCase() {
        assertThat(RoomSeatingPolicy.refuseTeamCreation(RoomPhase.SETUP, 1, "СОКОЛЫ", List.of("Соколы")))
                .contains(RoomSeatingPolicy.Refusal.TEAM_NAME_TAKEN);
        assertThat(RoomSeatingPolicy.refuseTeamCreation(RoomPhase.SETUP, 1, "  соколы ", List.of("Соколы")))
                .contains(RoomSeatingPolicy.Refusal.TEAM_NAME_TAKEN);
        assertThat(RoomSeatingPolicy.refuseTeamCreation(RoomPhase.SETUP, 1, "Стрижи", List.of("Соколы"))).isEmpty();
    }

    @Test
    @DisplayName("Распустить можно только пустую команду")
    void onlyEmptyTeamIsDeleted() {
        assertThat(RoomSeatingPolicy.refuseTeamDeletion(RoomPhase.SETUP, 0)).isEmpty();
        assertThat(RoomSeatingPolicy.refuseTeamDeletion(RoomPhase.SETUP, 1))
                .contains(RoomSeatingPolicy.Refusal.TEAM_NOT_EMPTY);
    }

    @Test
    @DisplayName("В команде помещаются двое, и место держится по живым")
    void teamHoldsTwo() {
        assertThat(RoomSeatingPolicy.refuseTeamJoin(RoomPhase.SETUP, 1, false)).isEmpty();
        assertThat(RoomSeatingPolicy.refuseTeamJoin(RoomPhase.SETUP, 2, false))
                .contains(RoomSeatingPolicy.Refusal.TEAM_IS_FULL);
        assertThat(RoomSeatingPolicy.refuseTeamJoin(RoomPhase.ACTIVE, 0, false))
                .contains(RoomSeatingPolicy.Refusal.SETUP_ONLY);
    }

    @Test
    @DisplayName("Повторное нажатие уже сидящего не отвечает «команда полна»")
    void sittingPlayerAlwaysPasses() {
        assertThat(RoomSeatingPolicy.refuseTeamJoin(RoomPhase.SETUP, 2, true)).isEmpty();
    }

    @Test
    @DisplayName("Уходящий из комнаты выходит из команды в любой фазе, а пересесть посреди партии нельзя")
    void leavingRoomAlwaysFreesTheSeat() {
        assertThat(RoomSeatingPolicy.refuseTeamLeave(RoomPhase.ACTIVE, true)).isEmpty();
        assertThat(RoomSeatingPolicy.refuseTeamLeave(RoomPhase.SETUP, false)).isEmpty();
        assertThat(RoomSeatingPolicy.refuseTeamLeave(RoomPhase.ACTIVE, false))
                .contains(RoomSeatingPolicy.Refusal.SETUP_ONLY);
    }

    @Test
    @DisplayName("Жеребьёвка раскладывает игроков по местам в присланном порядке")
    void drawFollowsShuffledOrder() {
        RoomSeatingPolicy.Draw draw = RoomSeatingPolicy.draw(
                List.of("p1", "p2", "p3"), List.of("t2", "t1", "t2", "t1"));

        assertThat(draw.membersByTeam().get("t2")).containsExactly("p1", "p3");
        assertThat(draw.membersByTeam().get("t1")).containsExactly("p2");
        assertThat(draw.benched()).isEmpty();
    }

    @Test
    @DisplayName("Лишние игроки остаются за столом, а команды без игроков всё равно объявлены")
    void extraPlayersAreBenched() {
        RoomSeatingPolicy.Draw draw = RoomSeatingPolicy.draw(
                List.of("p1", "p2", "p3"), List.of("t1", "t1"));

        assertThat(draw.membersByTeam()).containsOnlyKeys("t1");
        assertThat(draw.membersByTeam().get("t1")).containsExactly("p1", "p2");
        assertThat(draw.benched()).containsExactly("p3");
    }

    @Test
    @DisplayName("Жеребьёвке нужны две команды и хотя бы один живой игрок")
    void drawNeedsTeamsAndPlayers() {
        assertThat(RoomSeatingPolicy.refuseDraw(RoomPhase.SETUP, 2, 3)).isEmpty();
        assertThat(RoomSeatingPolicy.refuseDraw(RoomPhase.SETUP, 1, 3))
                .contains(RoomSeatingPolicy.Refusal.TEAMS_NOT_READY);
        assertThat(RoomSeatingPolicy.refuseDraw(RoomPhase.SETUP, 6, 3))
                .contains(RoomSeatingPolicy.Refusal.TEAMS_NOT_READY);
        assertThat(RoomSeatingPolicy.refuseDraw(RoomPhase.SETUP, 2, 0))
                .contains(RoomSeatingPolicy.Refusal.NO_ACTIVE_PLAYERS);
        assertThat(RoomSeatingPolicy.refuseDraw(RoomPhase.ACTIVE, 2, 3))
                .contains(RoomSeatingPolicy.Refusal.SETUP_ONLY);
    }

    @Test
    @DisplayName("Пришедшего из подбора сажают в самую пустую команду")
    void autoSeatPicksTheEmptiestTeam() {
        Map<String, Integer> alive = new LinkedHashMap<>();
        alive.put("t1", 1);
        alive.put("t2", 0);
        alive.put("t3", 2);

        assertThat(RoomSeatingPolicy.autoSeat(alive)).contains("t2");
    }

    @Test
    @DisplayName("Когда свободных мест нет, сажать некуда — и это не ошибка")
    void autoSeatReturnsNothingWhenFull() {
        Map<String, Integer> alive = new LinkedHashMap<>();
        alive.put("t1", 2);
        alive.put("t2", 2);

        assertThat(RoomSeatingPolicy.autoSeat(alive)).isEmpty();
    }

    @Test
    @DisplayName("При равной заполненности выбирается первая по порядку команда")
    void autoSeatIsStableOnATie() {
        Map<String, Integer> alive = new LinkedHashMap<>();
        alive.put("t1", 1);
        alive.put("t2", 1);

        assertThat(RoomSeatingPolicy.autoSeat(alive)).contains("t1");
    }
}
