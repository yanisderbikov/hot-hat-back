package ru.hothat.friend.usecase;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import ru.hothat.friend.store.FakeFriendshipStore;
import ru.hothat.support.Users;

import static ru.hothat.support.Refusals.allows;
import static ru.hothat.support.Refusals.refuses;

/** Право отвечать на заявку принадлежит адресату — и только ему. */
class FriendRequestAuthzTest {

    private static final long REQUEST = 17L;

    @Test
    @DisplayName("адресат вправе ответить на заявку")
    void addresseeMayAnswer() {
        FriendRequestAuthz authz = new FriendRequestAuthz(
                FakeFriendshipStore.withRequest(REQUEST, Users.ANN, Users.BOB));
        allows(() -> authz.assertRecipient(Users.player(Users.BOB), REQUEST));
    }

    @Test
    @DisplayName("отправитель не вправе принять свою же заявку за адресата")
    void requesterMayNotAnswerHisOwnRequest() {
        FriendRequestAuthz authz = new FriendRequestAuthz(
                FakeFriendshipStore.withRequest(REQUEST, Users.ANN, Users.BOB));
        refuses("REQUEST_FORBIDDEN", 403,
                () -> authz.assertRecipient(Users.player(Users.ANN), REQUEST));
    }

    @Test
    @DisplayName("посторонний не вправе ответить на чужую заявку")
    void strangerMayNotAnswer() {
        FriendRequestAuthz authz = new FriendRequestAuthz(
                FakeFriendshipStore.withRequest(REQUEST, Users.ANN, Users.BOB));
        refuses("REQUEST_FORBIDDEN", 403,
                () -> authz.assertRecipient(Users.player(Users.MALLORY), REQUEST));
    }

    @Test
    @DisplayName("несуществующая заявка отвечает тем же отказом: по номеру не узнать, есть ли она")
    void missingRequestLooksTheSame() {
        FriendRequestAuthz authz = new FriendRequestAuthz(FakeFriendshipStore.empty());
        refuses("REQUEST_FORBIDDEN", 403,
                () -> authz.assertRecipient(Users.player(Users.BOB), REQUEST));
    }
}
