package ru.hothat.support;

import ru.hothat.config.HotHatUser;

/** Личности запроса: столько, сколько нужно правилам доступа. */
public final class Users {

    public static final String ANN = "uid-ann";
    public static final String BOB = "uid-bob";
    /** Посторонний: ни места в комнате, ни дружбы, ни команды. */
    public static final String MALLORY = "uid-mallory";

    private Users() {
    }

    public static HotHatUser player(String uid) {
        return new HotHatUser(uid, uid + "@example.test", "", false, false, false);
    }

    public static HotHatUser admin(String uid) {
        return new HotHatUser(uid, uid + "@example.test", "", true, false, false);
    }
}
