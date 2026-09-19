package ru.hothat.support;

import ru.hothat.game.domain.RandomSource;

import java.util.ArrayList;
import java.util.List;

/**
 * Жребий партии без случайности.
 *
 * <p>Слово всегда берётся из начала шляпы, а перемешивание оставляет порядок
 * как есть. Тогда в тесте видно, какое слово выпадет следующим, и ожидание
 * пишется словом, а не «каким-то из трёх».
 *
 * <p>Перемешивание здесь переопределено намеренно: сам алгоритм Фишера — Йетса
 * проверяется отдельно, в {@code RandomSourceTest}, где жребий задан числами.
 */
public final class PredictableRandom implements RandomSource {

    @Override
    public int nextInt(int boundExclusive) {
        return 0;
    }

    @Override
    public <T> List<T> shuffle(List<T> values) {
        return new ArrayList<>(values);
    }
}
