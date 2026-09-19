package ru.hothat.util;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/** Fisher–Yates на криптостойком генераторе — как crypto.randomInt в JS. */
public final class Shuffle {

    private static final SecureRandom RANDOM = new SecureRandom();

    private Shuffle() {
    }

    public static <T> List<T> of(Collection<T> values) {
        List<T> copy = new ArrayList<>(values);
        for (int i = copy.size() - 1; i > 0; i--) {
            int j = RANDOM.nextInt(i + 1);
            T tmp = copy.get(i);
            copy.set(i, copy.get(j));
            copy.set(j, tmp);
        }
        return copy;
    }

    /** Достаёт случайный элемент, удаляя его из списка (takeRandom из test-bots.js). */
    public static <T> T take(List<T> values) {
        if (values.isEmpty()) {
            return null;
        }
        return values.remove(RANDOM.nextInt(values.size()));
    }

    public static <T> T pick(List<T> values) {
        return values.isEmpty() ? null : values.get(RANDOM.nextInt(values.size()));
    }
}
