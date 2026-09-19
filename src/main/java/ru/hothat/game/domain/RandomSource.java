package ru.hothat.game.domain;

import java.util.ArrayList;
import java.util.List;

/**
 * Источник случайности партии.
 *
 * <p>Существует ради проверяемости: {@code new Random()} внутри правил делает
 * «какое слово выпало» невоспроизводимым, а значит, тест на «мешок пустеет
 * ровно один раз» написать нельзя. Все места, где партия тянет жребий —
 * слово из шляпы и перемешивание шляпы, — проходят только сюда.
 */
@FunctionalInterface
public interface RandomSource {

    /** Целое из {@code [0, boundExclusive)}; при неположительной границе — 0. */
    int nextInt(int boundExclusive);

    /** Перемешивание Фишера — Йетса тем же жребием: своей случайности здесь нет. */
    default <T> List<T> shuffle(List<T> values) {
        List<T> copy = new ArrayList<>(values);
        for (int i = copy.size() - 1; i > 0; i--) {
            int j = nextInt(i + 1);
            T swap = copy.get(i);
            copy.set(i, copy.get(j));
            copy.set(j, swap);
        }
        return copy;
    }
}
