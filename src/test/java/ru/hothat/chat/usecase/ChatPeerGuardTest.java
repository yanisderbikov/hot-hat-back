package ru.hothat.chat.usecase;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import ru.hothat.support.FakeFriendships;
import ru.hothat.support.FakeRankedTeams;
import ru.hothat.support.Users;

import static org.assertj.core.api.Assertions.assertThat;
import static ru.hothat.support.Refusals.refuses;

/** Переписываться можно с другом и с напарником по рейтинговой команде. */
class ChatPeerGuardTest {

    @Test
    @DisplayName("другу написать можно")
    void friendPasses() {
        ChatPeerGuard guard = new ChatPeerGuard(
                FakeFriendships.empty().friends(Users.ANN, Users.BOB), FakeRankedTeams.empty());
        assertThat(guard.requirePeer(Users.player(Users.ANN), Users.BOB)).isEqualTo(Users.BOB);
    }

    @Test
    @DisplayName("напарнику по подтверждённой паре — тоже, даже если он не друг")
    void teammatePasses() {
        ChatPeerGuard guard = new ChatPeerGuard(
                FakeFriendships.empty(), FakeRankedTeams.of("active", Users.ANN, Users.BOB));
        assertThat(guard.requirePeer(Users.player(Users.ANN), Users.BOB)).isEqualTo(Users.BOB);
    }

    @Test
    @DisplayName("постороннему написать нельзя")
    void strangerIsRefused() {
        ChatPeerGuard guard = new ChatPeerGuard(
                FakeFriendships.empty().friends(Users.ANN, Users.BOB), FakeRankedTeams.empty());
        refuses("FRIEND_REQUIRED", 403,
                () -> guard.requirePeer(Users.player(Users.ANN), Users.MALLORY));
    }

    @Test
    @DisplayName("неподтверждённая пара права на переписку ещё не даёт")
    void pendingTeammateIsRefused() {
        ChatPeerGuard guard = new ChatPeerGuard(
                FakeFriendships.empty(), FakeRankedTeams.of("pending", Users.ANN, Users.BOB));
        refuses("FRIEND_REQUIRED", 403, () -> guard.requirePeer(Users.player(Users.ANN), Users.BOB));
    }

    @Test
    @DisplayName("чужая команда не делает напарником: в её составе меня нет")
    void foreignTeamDoesNotHelp() {
        ChatPeerGuard guard = new ChatPeerGuard(
                FakeFriendships.empty(), FakeRankedTeams.of("active", Users.BOB, "uid-cat"));
        refuses("FRIEND_REQUIRED", 403,
                () -> guard.requirePeer(Users.player(Users.MALLORY), Users.BOB));
    }

    @Test
    @DisplayName("сам себе не собеседник, и собеседник без имени — тоже")
    void selfAndEmptyPeerAreRefused() {
        ChatPeerGuard guard = new ChatPeerGuard(FakeFriendships.empty(), FakeRankedTeams.empty());
        refuses("FRIEND_REQUIRED", 409, () -> guard.requirePeer(Users.player(Users.ANN), Users.ANN));
        refuses("FRIEND_REQUIRED", 409, () -> guard.requirePeer(Users.player(Users.ANN), null));
        refuses("FRIEND_REQUIRED", 409, () -> guard.requirePeer(Users.player(Users.ANN), ""));
    }
}
