package ru.hothat.room.domain;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RoomCleanupPolicyTest {
    private static final long NOW = 1_000_000L;

    private static RoomCleanupPolicy.Occupancy testRoom(long vacantSince, Long... humans) {
        return new RoomCleanupPolicy.Occupancy(RoomPhase.SETUP, true,
                NOW - 120_000L, vacantSince, List.of(humans));
    }

    @Test
    void humanHeartbeatProtectsTestRoom() {
        assertThat(RoomCleanupPolicy.verdict(testRoom(0, NOW - 5_000L), false, NOW).abandoned())
                .isFalse();
    }

    @Test
    void testRoomWithBotsOnlyWaitsThirtySecondsAfterLastHumanLeaves() {
        assertThat(RoomCleanupPolicy.verdict(testRoom(NOW - 29_999L), true, NOW).abandoned())
                .isFalse();
        RoomCleanupPolicy.Verdict expired = RoomCleanupPolicy.verdict(
                testRoom(NOW - 30_000L), false, NOW);
        assertThat(expired.abandoned()).isTrue();
        assertThat(expired.reason()).isEqualTo("test-room-empty-30s");
    }

    @Test
    void disconnectedHumanSeatExpiresAtThirtySecondsInTestRoom() {
        assertThat(RoomCleanupPolicy.verdict(testRoom(0, NOW - 29_999L), false, NOW).abandoned())
                .isFalse();
        assertThat(RoomCleanupPolicy.verdict(testRoom(0, NOW - 30_000L), false, NOW).abandoned())
                .isTrue();
    }

    @Test
    void ordinaryRoomRetainsCreationGrace() {
        RoomCleanupPolicy.Occupancy room = new RoomCleanupPolicy.Occupancy(
                RoomPhase.SETUP, false, NOW - 119_000L, 0L, List.of());
        assertThat(RoomCleanupPolicy.verdict(room, false, NOW).abandoned()).isFalse();
        assertThat(RoomCleanupPolicy.verdict(room, true, NOW).abandoned()).isTrue();
    }

    @Test
    void ordinaryActiveGameExpiresEarlierThanIdleSetupRoom() {
        RoomCleanupPolicy.Occupancy playing = new RoomCleanupPolicy.Occupancy(
                RoomPhase.ACTIVE, false, NOW - 1_000_000L, 0L, List.of(NOW - 400_000L));
        RoomCleanupPolicy.Occupancy setup = new RoomCleanupPolicy.Occupancy(
                RoomPhase.SETUP, false, NOW - 1_000_000L, 0L, List.of(NOW - 400_000L));
        assertThat(RoomCleanupPolicy.verdict(playing, false, NOW).abandoned()).isTrue();
        assertThat(RoomCleanupPolicy.verdict(setup, false, NOW).abandoned()).isFalse();
    }
}
