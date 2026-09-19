package ru.hothat.game.domain;

import ru.hothat.sabotage.spi.SabotageArmoryPort;

import java.util.ArrayList;
import java.util.List;

/**
 * Круговая очередь мемов игрока: доступные, резерв и переработка.
 *
 * <p>Три списка — это три состояния одного и того же ролика из обоймы, а не
 * три коллекции. Ролик выходит из резерва в доступные наградой, из доступных
 * в переработку выстрелом и возвращается из переработки, когда резерв пуст.
 * Пока состояния лежали тремя голыми списками в чужом классе, каждый
 * переход писался заново по месту вызова — отсюда и брались очереди, где один
 * и тот же мем числился и доступным, и отстрелянным.
 *
 * <p>Курсор нужен на случай, когда пусты и резерв, и переработка: тогда мем
 * выдаётся по кругу, и без курсора это всегда был бы первый слот обоймы.
 */
public final class MemeQueue {

    private final List<String> loadout;
    private final List<String> available;
    private final List<String> reserve;
    private final List<String> recycle;
    private int cycleCursor;

    private MemeQueue(List<String> loadout, List<String> available, List<String> reserve,
                      List<String> recycle, int cycleCursor) {
        this.loadout = loadout;
        this.available = available;
        this.reserve = reserve;
        this.recycle = recycle;
        this.cycleCursor = Math.max(0, cycleCursor);
    }

    /**
     * Очередь по сохранённым спискам.
     *
     * <p>Всё, чего нет в обойме, отбрасывается: обойму игрок меняет между
     * партиями, и мем, выпавший из неё, не должен остаться доступным.
     */
    public static MemeQueue of(List<String> loadout, List<String> available, List<String> reserve,
                               List<String> recycle, int cycleCursor) {
        List<String> slots = normalizedLoadout(loadout);
        return new MemeQueue(slots, filterToLoadout(available, slots), filterToLoadout(reserve, slots),
                filterToLoadout(recycle, slots), cycleCursor);
    }

    /** Обойма без пустот и повторов, не длиннее {@link SabotageArmoryPort#LOADOUT_SIZE}. */
    public static List<String> normalizedLoadout(List<String> memeIds) {
        List<String> slots = new ArrayList<>();
        for (String id : memeIds == null ? List.<String>of() : memeIds) {
            if (id != null && !id.isBlank() && !slots.contains(id)) {
                slots.add(id);
            }
            if (slots.size() >= SabotageArmoryPort.LOADOUT_SIZE) {
                break;
            }
        }
        return slots;
    }

    public List<String> loadout() {
        return List.copyOf(loadout);
    }

    public List<String> available() {
        return List.copyOf(available);
    }

    public List<String> reserve() {
        return List.copyOf(reserve);
    }

    public List<String> recycle() {
        return List.copyOf(recycle);
    }

    public int cycleCursor() {
        return cycleCursor;
    }

    /** Заряжен ли ролик в обойму этой партии. */
    public boolean loaded(String memeId) {
        return loadout.contains(memeId);
    }

    /** Готов ли ролик к выстрелу прямо сейчас. */
    public boolean ready(String memeId) {
        return available.contains(memeId);
    }

    public String slot(int index) {
        return loadout.get(index);
    }

    /**
     * Выдача {@code amount} мемов: сначала резерв, потом переработка, потом по
     * кругу с курсора.
     *
     * <p>Пустая обойма не выдаёт ничего и не зацикливается: игрок может войти
     * в партию без заряженных мемов, и награда за слова ему всё равно идёт.
     */
    public MemeQueue grant(int amount) {
        int left = Math.max(0, amount);
        while (left > 0 && !loadout.isEmpty()) {
            String next;
            if (!reserve.isEmpty()) {
                next = reserve.remove(0);
            } else if (!recycle.isEmpty()) {
                next = recycle.remove(0);
            } else {
                next = loadout.get(cycleCursor % loadout.size());
                cycleCursor = (cycleCursor + 1) % loadout.size();
            }
            available.add(next);
            left--;
        }
        return this;
    }

    /** Выстрел: ролик уходит из доступных в переработку и вернётся по кругу. */
    public void burn(String memeId) {
        available.remove(memeId);
        recycle.add(memeId);
    }

    /**
     * Замена ролика в слоте обоймы.
     *
     * <p>Меняется во всех трёх очередях сразу: иначе прежний ролик остался бы
     * доступным к выстрелу, а новый не появился бы нигде.
     */
    public void replaceSlot(int index, String nextMemeId) {
        String previous = loadout.get(index);
        loadout.set(index, nextMemeId);
        replace(available, previous, nextMemeId);
        replace(reserve, previous, nextMemeId);
        replace(recycle, previous, nextMemeId);
    }

    private static void replace(List<String> values, String from, String to) {
        for (int i = 0; i < values.size(); i++) {
            if (from.equals(values.get(i))) {
                values.set(i, to);
            }
        }
    }

    private static List<String> filterToLoadout(List<String> ids, List<String> loadout) {
        List<String> result = new ArrayList<>();
        for (String id : ids == null ? List.<String>of() : ids) {
            if (loadout.contains(id)) {
                result.add(id);
            }
        }
        return result;
    }
}
