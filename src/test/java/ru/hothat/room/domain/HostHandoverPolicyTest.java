package ru.hothat.room.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Сторож хозяйства комнаты: когда права уходят и когда возвращаются. */
class HostHandoverPolicyTest {

    private static final long NOW = 1_000_000L;
    private static final List<String> FOUR_ALIVE = List.of("uid-host", "uid-b", "uid-c", "uid-d");

    private static HostHandoverPolicy.Facts facts(RoomPhase phase, boolean privateRoom, String previousHost,
                                                  long lastActivity, List<String> alive) {
        return new HostHandoverPolicy.Facts(phase, privateRoom, 4, "uid-host", previousHost, lastActivity, alive);
    }

    @Test
    @DisplayName("Вне набора сторож не работает: посреди партии хозяйство не передают")
    void watchIsSetupOnly() {
        HostHandoverPolicy.Decision decision =
                HostHandoverPolicy.decide(facts(RoomPhase.ACTIVE, false, null, 1, FOUR_ALIVE), NOW);

        assertThat(decision.outcome()).isEqualTo(HostHandoverOutcome.NOT_APPLICABLE);
    }

    @Test
    @DisplayName("В приватной комнате хозяйство не отнимают")
    void privateRoomKeepsItsHost() {
        HostHandoverPolicy.Decision decision =
                HostHandoverPolicy.decide(facts(RoomPhase.SETUP, true, null, 1, FOUR_ALIVE), NOW);

        assertThat(decision.outcome()).isEqualTo(HostHandoverOutcome.PRIVATE_ROOM);
    }

    @Test
    @DisplayName("Вернувшийся прежний хозяин получает комнату обратно, не дожидаясь полного состава")
    void returningHostIsRestoredImmediately() {
        HostHandoverPolicy.Decision decision = HostHandoverPolicy.decide(
                facts(RoomPhase.SETUP, false, "uid-b", NOW, List.of("uid-host", "uid-b")), NOW);

        assertThat(decision.outcome()).isEqualTo(HostHandoverOutcome.RESTORED);
        assertThat(decision.newHostUid()).isEqualTo("uid-b");
        assertThat(decision.resetActivityClock()).isTrue();
    }

    @Test
    @DisplayName("Прежний хозяин, который и есть нынешний, ничего не возвращает")
    void currentHostIsNotRestoredToHimself() {
        HostHandoverPolicy.Decision decision = HostHandoverPolicy.decide(
                facts(RoomPhase.SETUP, false, "uid-host", NOW, FOUR_ALIVE), NOW);

        assertThat(decision.outcome()).isEqualTo(HostHandoverOutcome.COUNTDOWN);
    }

    @Test
    @DisplayName("Пока комната не укомплектована, бездействие хозяина никому не мешает")
    void incompleteRoomWaitsForPlayers() {
        HostHandoverPolicy.Decision decision = HostHandoverPolicy.decide(
                facts(RoomPhase.SETUP, false, null, 1, List.of("uid-host", "uid-b")), NOW);

        assertThat(decision.outcome()).isEqualTo(HostHandoverOutcome.WAITING_FOR_PLAYERS);
        assertThat(decision.alivePlayers()).isEqualTo(2);
        assertThat(decision.target()).isEqualTo(4);
    }

    @Test
    @DisplayName("Только что укомплектованная комната даёт хозяину полные три минуты")
    void freshlyFilledRoomStartsTheClock() {
        HostHandoverPolicy.Decision decision = HostHandoverPolicy.decide(
                facts(RoomPhase.SETUP, false, null, 0, FOUR_ALIVE), NOW);

        assertThat(decision.outcome()).isEqualTo(HostHandoverOutcome.COUNTDOWN);
        assertThat(decision.remainingMs()).isEqualTo(HostHandoverPolicy.IDLE_LIMIT_MS);
        assertThat(decision.resetActivityClock()).isTrue();
    }

    @Test
    @DisplayName("Пока три минуты не вышли, идёт отсчёт с остатком")
    void countdownShowsRemainingTime() {
        long lastActivity = NOW - HostHandoverPolicy.IDLE_LIMIT_MS + 30_000;

        HostHandoverPolicy.Decision decision = HostHandoverPolicy.decide(
                facts(RoomPhase.SETUP, false, null, lastActivity, FOUR_ALIVE), NOW);

        assertThat(decision.outcome()).isEqualTo(HostHandoverOutcome.COUNTDOWN);
        assertThat(decision.remainingMs()).isEqualTo(30_000);
        assertThat(decision.resetActivityClock()).isFalse();
    }

    @Test
    @DisplayName("После трёх минут бездействия хозяйство переходит первому живому участнику")
    void idleHostLosesTheRoom() {
        long lastActivity = NOW - HostHandoverPolicy.IDLE_LIMIT_MS;

        HostHandoverPolicy.Decision decision = HostHandoverPolicy.decide(
                facts(RoomPhase.SETUP, false, null, lastActivity, FOUR_ALIVE), NOW);

        assertThat(decision.outcome()).isEqualTo(HostHandoverOutcome.TRANSFERRED);
        assertThat(decision.newHostUid()).isEqualTo("uid-b");
        assertThat(decision.resetActivityClock()).isTrue();
    }

    @Test
    @DisplayName("Комната на двоих сторожу не интересна: цель всё равно не меньше четверых")
    void smallRoomsNeverReachTheTarget() {
        HostHandoverPolicy.Facts small = new HostHandoverPolicy.Facts(
                RoomPhase.SETUP, false, 2, "uid-host", null, 1, List.of("uid-host", "uid-b"));

        HostHandoverPolicy.Decision decision = HostHandoverPolicy.decide(small, NOW);

        assertThat(decision.target()).isEqualTo(HostHandoverPolicy.MIN_TARGET_PLAYERS);
        assertThat(decision.outcome()).isEqualTo(HostHandoverOutcome.WAITING_FOR_PLAYERS);
    }
}
