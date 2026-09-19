package ru.hothat.game.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Круговая очередь мемов: выдача, выстрел и замена слота.
 *
 * <p>Правила очереди проверяются здесь напрямую, потому что порядок выдачи —
 * не подробность: он решает, увидит ли команда за партию все пять роликов
 * игрока или один и тот же четыре раза.
 */
class MemeQueueTest {

    private static final List<String> FIVE = List.of("m1", "m2", "m3", "m4", "m5");

    private static MemeQueue armed() {
        return MemeQueue.of(FIVE, List.of("m1", "m2"), List.of("m3", "m4", "m5"), List.of(), 0);
    }

    @Test
    @DisplayName("Обойма режется до пяти слотов, повторы и пустые строки в неё не попадают")
    void loadoutIsNormalized() {
        List<String> loadout = MemeQueue.normalizedLoadout(
                new ArrayList<>(List.of("m1", "m1", "  ", "m2", "m3", "m4", "m5", "m6")));

        assertThat(loadout).containsExactly("m1", "m2", "m3", "m4", "m5");
    }

    @Test
    @DisplayName("Мем, выпавший из обоймы между партиями, не остаётся доступным")
    void queueKeepsOnlyWhatIsInTheLoadout() {
        MemeQueue queue = MemeQueue.of(List.of("m1", "m2"), List.of("m1", "выброшенный"),
                List.of("m2"), List.of("тоже-выброшенный"), 0);

        assertThat(queue.available()).containsExactly("m1");
        assertThat(queue.reserve()).containsExactly("m2");
        assertThat(queue.recycle()).isEmpty();
    }

    @Test
    @DisplayName("Награда берёт мем из резерва, пока резерв не кончится")
    void grantPrefersReserve() {
        MemeQueue queue = armed().grant(2);

        assertThat(queue.available()).containsExactly("m1", "m2", "m3", "m4");
        assertThat(queue.reserve()).containsExactly("m5");
    }

    @Test
    @DisplayName("Пока в резерве есть невиданный мем, отстрелянный второй раз не выдаётся")
    void reserveWinsOverRecycle() {
        MemeQueue queue = MemeQueue.of(FIVE, List.of("m2"), List.of("m4", "m5"), List.of("m1", "m3"), 0);

        queue.grant(1);

        assertThat(queue.available()).containsExactly("m2", "m4");
        assertThat(queue.recycle()).containsExactly("m1", "m3");
    }

    @Test
    @DisplayName("Когда резерв пуст, награда возвращает отстрелянный мем из переработки")
    void grantFallsBackToRecycle() {
        MemeQueue queue = MemeQueue.of(FIVE, List.of("m3"), List.of(), List.of("m1", "m2"), 0).grant(1);

        assertThat(queue.available()).containsExactly("m3", "m1");
        assertThat(queue.recycle()).containsExactly("m2");
    }

    @Test
    @DisplayName("Когда пусты и резерв, и переработка, мемы идут по кругу с курсора, а не с первого слота")
    void grantCyclesFromCursor() {
        MemeQueue queue = MemeQueue.of(FIVE, List.of(), List.of(), List.of(), 3).grant(3);

        assertThat(queue.available()).containsExactly("m4", "m5", "m1");
        assertThat(queue.cycleCursor()).isEqualTo(1);
    }

    @Test
    @DisplayName("Пустая обойма не выдаёт ничего и не зацикливается")
    void grantOnEmptyLoadoutStops() {
        MemeQueue queue = MemeQueue.of(List.of(), List.of(), List.of(), List.of(), 0).grant(4);

        assertThat(queue.available()).isEmpty();
        assertThat(queue.cycleCursor()).isZero();
    }

    @Test
    @DisplayName("Выстрел уводит мем из доступных в переработку, а не удаляет его из партии")
    void burnMovesMemeToRecycle() {
        MemeQueue queue = armed();

        queue.burn("m1");

        assertThat(queue.available()).containsExactly("m2");
        assertThat(queue.recycle()).containsExactly("m1");
        assertThat(queue.loaded("m1")).isTrue();
        assertThat(queue.ready("m1")).isFalse();
    }

    @Test
    @DisplayName("Отстрелянный мем возвращается наградой, когда резерв кончился")
    void burnedMemeComesBackWithTheNextReward() {
        MemeQueue queue = MemeQueue.of(FIVE, List.of("m1"), List.of(), List.of(), 0);

        queue.burn("m1");
        queue.grant(1);

        assertThat(queue.available()).containsExactly("m1");
        assertThat(queue.recycle()).isEmpty();
    }

    @Test
    @DisplayName("Замена слота меняет мем во всех трёх очередях сразу")
    void replaceSlotUpdatesEveryQueue() {
        MemeQueue queue = MemeQueue.of(FIVE, List.of("m1", "m2"), List.of("m4", "m5"), List.of("m3"), 0);

        queue.replaceSlot(2, "m9");

        assertThat(queue.loadout()).containsExactly("m1", "m2", "m9", "m4", "m5");
        assertThat(queue.recycle()).containsExactly("m9");
        assertThat(queue.available()).containsExactly("m1", "m2");
        assertThat(queue.loaded("m3")).isFalse();
    }

    @Test
    @DisplayName("Очередь отвечает копиями: снаружи её списки не переписать")
    void queueDoesNotLeakItsLists() {
        MemeQueue queue = armed();

        assertThat(queue.available()).isUnmodifiable();
        assertThat(queue.loadout()).isUnmodifiable();
    }
}
