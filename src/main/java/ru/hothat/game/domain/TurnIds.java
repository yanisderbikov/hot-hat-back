package ru.hothat.game.domain;

/**
 * Идентификаторы хода и диверсии.
 *
 * <p>Собственный генератор, а не {@code Ids.hex}, по одной причине: тот берёт
 * случайность из своего {@code SecureRandom}, и партию с таким генератором
 * нельзя воспроизвести в тесте. Здесь жребий приходит извне — тот же самый,
 * которым тянется слово из шляпы.
 */
public final class TurnIds {

    private static final String ALPHABET = "0123456789abcdef";

    private TurnIds() {
    }

    public static String turn(RandomSource random) {
        return "turn_" + hex(random, 16);
    }

    public static String sabotage(RandomSource random) {
        return "sab_" + hex(random, 20);
    }

    public static String clip(RandomSource random) {
        return "repl_" + hex(random, 20);
    }

    private static String hex(RandomSource random, int length) {
        StringBuilder builder = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            builder.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        }
        return builder.toString();
    }
}
