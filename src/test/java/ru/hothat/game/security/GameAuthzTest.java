package ru.hothat.game.security;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import ru.hothat.config.HotHatUser;
import ru.hothat.game.domain.MatchPhase;
import ru.hothat.game.domain.MatchState;
import ru.hothat.game.store.MatchStore;
import ru.hothat.support.FakeRoomLifecycle;
import ru.hothat.support.Matches;
import ru.hothat.support.Users;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static ru.hothat.support.Refusals.refuses;

/**
 * Предикаты прав области партии: кто вправе вести ход, а кто только смотрит.
 *
 * <p>Личность приходит из контекста безопасности, поэтому каждый случай её
 * туда и кладёт: предикат читает того, кто пришёл с запросом, а не того, кого
 * ему передали аргументом.
 */
class GameAuthzTest {

    private static final String ROOM = "hat-0123456789abcdef";

    @AfterEach
    void clearIdentity() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("хозяин комнаты распоряжается партией, участник — нет")
    void hostOnly() {
        FakeRoomLifecycle rooms = FakeRoomLifecycle.empty().host(Matches.ANN).member(Matches.BOB);

        signIn(Matches.ANN);
        assertThat(authz(rooms, live()).isHost(ROOM)).isTrue();

        signIn(Matches.BOB);
        refuses("HOST_ONLY", 403, () -> authz(rooms, live()).isHost(ROOM));
    }

    @Test
    @DisplayName("участник комнаты проходит, посторонний получает отказ по имени")
    void memberOnly() {
        FakeRoomLifecycle rooms = FakeRoomLifecycle.empty().host(Matches.ANN).member(Matches.BOB);

        signIn(Matches.BOB);
        assertThat(authz(rooms, live()).isMember(ROOM)).isTrue();

        signIn(Users.MALLORY);
        refuses("ROOM_MEMBER_ONLY", 403, () -> authz(rooms, live()).isMember(ROOM));
    }

    @Test
    @DisplayName("зритель видит партию, но участником комнаты не считается")
    void spectatorWatchesButDoesNotPlay() {
        FakeRoomLifecycle rooms = FakeRoomLifecycle.empty()
                .host(Matches.ANN).spectator(Users.MALLORY);

        signIn(Users.MALLORY);
        assertThat(authz(rooms, live()).isMemberOrSpectator(ROOM)).isTrue();
        refuses("ROOM_MEMBER_ONLY", 403, () -> authz(rooms, live()).isMember(ROOM));
    }

    @Test
    @DisplayName("зашедший посреди партии сидит в комнате, но игроком партии не становится")
    void latecomerIsNotAPlayer() {
        // Составы заморожены на старте: место в комнате у человека есть,
        // а строки в составе — нет.
        FakeRoomLifecycle rooms = FakeRoomLifecycle.empty()
                .host(Matches.ANN).member(Matches.BOB).member(Users.MALLORY);

        signIn(Matches.BOB);
        assertThat(authz(rooms, live()).isPlayer(ROOM)).isTrue();

        signIn(Users.MALLORY);
        refuses("PLAYER_NOT_FOUND", 403, () -> authz(rooms, live()).isPlayer(ROOM));
    }

    @Test
    @DisplayName("до старта партии игрок — это любой участник комнаты: обойму заряжают там")
    void beforeStartAnyMemberIsAPlayer() {
        FakeRoomLifecycle rooms = FakeRoomLifecycle.empty()
                .host(Matches.ANN).member(Users.MALLORY);

        signIn(Users.MALLORY);
        assertThat(authz(rooms, Matches.room()).isPlayer(ROOM)).isTrue();
    }

    @Test
    @DisplayName("пока ход не начат, объяснять вправе любой из активной команды")
    void beforeTurnBothOfActiveTeamMayStart() {
        FakeRoomLifecycle rooms = allFour();
        MatchState intro = live();
        intro.setPhase(MatchPhase.TURN_INTRO);
        intro.setExplainerUid(null);

        signIn(Matches.ANN);
        assertThat(authz(rooms, intro).isExplainer(ROOM)).isTrue();
        signIn(Matches.BOB);
        assertThat(authz(rooms, intro).isExplainer(ROOM)).isTrue();
    }

    @Test
    @DisplayName("в идущем ходе объясняет один: напарник и соперник — уже нет")
    void duringTurnOnlyTheExplainer() {
        FakeRoomLifecycle rooms = allFour();
        MatchState turn = live();
        turn.setPhase(MatchPhase.ACTIVE);
        turn.setExplainerUid(Matches.ANN);

        signIn(Matches.ANN);
        assertThat(authz(rooms, turn).isExplainer(ROOM)).isTrue();

        signIn(Matches.BOB);
        refuses("TURN_NOT_YOURS", 403, () -> authz(rooms, turn).isExplainer(ROOM));

        signIn(Matches.CAT);
        refuses("TURN_NOT_YOURS", 403, () -> authz(rooms, turn).isExplainer(ROOM));
    }

    @Test
    @DisplayName("ход чужой команды не даёт права объяснять")
    void otherTeamMayNotExplain() {
        FakeRoomLifecycle rooms = allFour();
        MatchState intro = live();
        intro.setPhase(MatchPhase.TURN_INTRO);

        signIn(Matches.CAT);
        refuses("TURN_NOT_YOURS", 403, () -> authz(rooms, intro).isExplainer(ROOM));
    }

    @Test
    @DisplayName("без личности предикат отвечает ложью, а не отказом: невошедшего разбирает общая цепочка")
    void anonymousGetsPlainFalse() {
        SecurityContextHolder.clearContext();
        assertThat(authz(FakeRoomLifecycle.empty().host(Matches.ANN), live()).isMember(ROOM))
                .isFalse();
    }

    @Test
    @DisplayName("повторный вопрос отвечается из кеша, а не вторым чтением комнаты")
    void answerIsCachedWithinRequest() {
        FakeRoomLifecycle rooms = FakeRoomLifecycle.empty().host(Matches.ANN);
        signIn(Matches.ANN);
        GameAuthz authz = authz(rooms, live());

        assertThat(authz.isMember(ROOM)).isTrue();
        assertThat(authz.isMember(ROOM)).isTrue();
        assertThat(rooms.membershipReads()).isEqualTo(1);
    }

    private static FakeRoomLifecycle allFour() {
        return FakeRoomLifecycle.empty()
                .host(Matches.ANN).member(Matches.BOB).member(Matches.CAT).member(Matches.DAN);
    }

    /** Идущая партия: две пары по составам, ход у красных. */
    private static MatchState live() {
        MatchState state = Matches.room();
        state.setPhase(MatchPhase.ACTIVE);
        state.setTeamOrder(List.of(Matches.RED, Matches.BLUE));
        state.setRosters(Map.of(Matches.RED, List.of(Matches.ANN, Matches.BOB),
                Matches.BLUE, List.of(Matches.CAT, Matches.DAN)));
        state.setCurrentTeamId(Matches.RED);
        state.setExplainerUid(Matches.ANN);
        return state;
    }

    private static void signIn(String uid) {
        HotHatUser user = Users.player(uid);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, "n/a", List.of()));
    }

    private static GameAuthz authz(FakeRoomLifecycle rooms, MatchState state) {
        return new GameAuthz(rooms, new MatchStore(null, null, null, null) {
            @Override
            public MatchState read(String roomId) {
                return state;
            }
        });
    }
}
