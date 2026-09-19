package ru.hothat.friend.usecase;

/**
 * Пределы чтений дружбы: сколько строк адрес забирает из хранилища за раз.
 *
 * <p>Числа перенесены из старого движка ({@code SocialServiceImpl.friends}:
 * {@code getLinksOf(uid, 100)} и соседние вызовы) без изменений — переезд на v2
 * не должен менять то, что видит игрок. Держатся вместе, а не по use-case,
 * потому что их называет и ответ: {@code limit} в теле обязан совпадать с тем,
 * с чем ходили в базу, иначе клиент не поймёт, полон ли список.
 */
public final class FriendReadLimits {

    /** Связей дружбы за раз — им же ограничено и присутствие. */
    public static final int FRIENDS = 100;

    /** Входящих заявок, ждущих ответа. */
    public static final int INCOMING = 50;

    /** Исходящих заявок в любом состоянии: ждущие и принятые едут одним списком. */
    public static final int OUTGOING = 80;

    private FriendReadLimits() {
    }
}
