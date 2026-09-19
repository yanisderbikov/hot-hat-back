package ru.hothat.lobby.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Кому какая комната видна в витрине и кого считать живым игроком. */
class LobbyVisibilityTest {

    private static final long NOW = 10_000_000L;

    private static LobbyVisibility.RoomFacts open() {
        return new LobbyVisibility.RoomFacts(false, false, false, false, false, null);
    }

    @Test
    @DisplayName("Обычная открытая комната видна всем, включая незалогиненного гостя")
    void openRoomIsVisibleToEveryone() {
        assertThat(LobbyVisibility.visibleTo(open(), "uid-any")).isTrue();
        assertThat(LobbyVisibility.visibleTo(open(), null)).isTrue();
    }

    @Test
    @DisplayName("Закрытую комнату не видит никто")
    void closedRoomIsHidden() {
        LobbyVisibility.RoomFacts closed = new LobbyVisibility.RoomFacts(true, false, false, false, false, null);

        assertThat(LobbyVisibility.visibleTo(closed, "uid-any")).isFalse();
    }

    @Test
    @DisplayName("Приватную комнату в витрине не показывают: в неё зовут по ссылке")
    void privateRoomIsHidden() {
        LobbyVisibility.RoomFacts privateRoom =
                new LobbyVisibility.RoomFacts(false, true, false, false, false, null);

        assertThat(LobbyVisibility.visibleTo(privateRoom, "uid-any")).isFalse();
    }

    @Test
    @DisplayName("Комната подбора показывается только когда состав собран")
    void managedRoomAppearsOnlyWhenReady() {
        LobbyVisibility.RoomFacts collecting =
                new LobbyVisibility.RoomFacts(false, false, true, false, false, null);
        LobbyVisibility.RoomFacts ready =
                new LobbyVisibility.RoomFacts(false, false, true, true, false, null);

        assertThat(LobbyVisibility.visibleTo(collecting, "uid-any")).isFalse();
        assertThat(LobbyVisibility.visibleTo(ready, "uid-any")).isTrue();
    }

    @Test
    @DisplayName("Тестовую комнату видит только её хозяин")
    void testRoomIsVisibleToItsOwnerOnly() {
        LobbyVisibility.RoomFacts testRoom =
                new LobbyVisibility.RoomFacts(false, false, false, false, true, "uid-owner");

        assertThat(LobbyVisibility.visibleTo(testRoom, "uid-owner")).isTrue();
        assertThat(LobbyVisibility.visibleTo(testRoom, "uid-other")).isFalse();
        assertThat(LobbyVisibility.visibleTo(testRoom, null)).isFalse();
    }

    @Test
    @DisplayName("Закрытая тестовая комната не видна и хозяину")
    void closedTestRoomIsHiddenEvenFromOwner() {
        LobbyVisibility.RoomFacts closedTestRoom =
                new LobbyVisibility.RoomFacts(true, false, false, false, true, "uid-owner");

        assertThat(LobbyVisibility.visibleTo(closedTestRoom, "uid-owner")).isFalse();
    }

    @Test
    @DisplayName("Игрок живой ровно четыре минуты после последнего появления")
    void playerIsAliveForFourMinutes() {
        long window = LobbyVisibility.PLAYER_ALIVE_WINDOW_MS;

        assertThat(LobbyVisibility.playerAlive(false, NOW - window, NOW)).isTrue();
        assertThat(LobbyVisibility.playerAlive(false, NOW - window - 1, NOW)).isFalse();
    }

    @Test
    @DisplayName("Тест-бот живой всегда: он не отмечается присутствием")
    void testBotIsAlwaysAlive() {
        assertThat(LobbyVisibility.playerAlive(true, 0, NOW)).isTrue();
    }

    @Test
    @DisplayName("Счётчику игроков верят полторы минуты, а нулевому не верят вовсе")
    void publicCountIsTrustedForNinetySeconds() {
        long fresh = LobbyVisibility.PUBLIC_COUNT_FRESH_MS;

        assertThat(LobbyVisibility.publicCountFresh(NOW - fresh + 1, NOW)).isTrue();
        assertThat(LobbyVisibility.publicCountFresh(NOW - fresh, NOW)).isFalse();
        assertThat(LobbyVisibility.publicCountFresh(0, NOW)).isFalse();
    }
}
