package ru.hothat.game.domain;

import ru.hothat.sabotage.spi.SabotageArmoryPort;

import java.util.ArrayList;
import java.util.List;

/**
 * Обойма мемов: пять слотов и круговая очередь выдачи.
 *
 * <p>Правила доступа к очереди через {@link MatchPlayer}: сама очередь и её
 * переходы живут в {@link MemeQueue}, а здесь остаётся только перекладывание
 * между игроком партии и ею. Из-за этого правило не зависит ни от строки
 * {@code room_player}, ни от чужой карты состояния тест-бота.
 */
public final class LoadoutRules {

    /** Сколько мемов игрок заряжает перед партией; число называет владелец обоймы. */
    public static final int SIZE = SabotageArmoryPort.LOADOUT_SIZE;

    private LoadoutRules() {
    }

    /** Обойма без пустот и повторов, не длиннее пяти. */
    public static List<String> normalize(List<String> memeIds) {
        return MemeQueue.normalizedLoadout(memeIds);
    }

    public static boolean charged(List<String> memeIds) {
        return normalize(memeIds).size() == SIZE;
    }

    /** Очередь игрока такой, какой она сохранена в партии. */
    public static MemeQueue queue(MatchPlayer player) {
        return MemeQueue.of(player.getLoadout(), player.getAvailable(), player.getReserve(),
                player.getRecycle(), player.getCycleCursor());
    }

    /** Выдать игроку {@code amount} мемов: резерв, потом переработка, потом по кругу. */
    public static void grant(MatchPlayer player, int amount) {
        if (amount <= 0) {
            return;
        }
        apply(player, queue(player).grant(amount));
    }

    public static void apply(MatchPlayer player, MemeQueue queue) {
        player.setLoadout(new ArrayList<>(queue.loadout()));
        player.setAvailable(new ArrayList<>(queue.available()));
        player.setReserve(new ArrayList<>(queue.reserve()));
        player.setRecycle(new ArrayList<>(queue.recycle()));
        player.setCycleCursor(queue.cycleCursor());
    }

    /**
     * Начало партии: боезапас базовый, первые четыре мема доступны, остальные
     * ждут в резерве. Четыре — это стартовый заряд ключа {@code meme}, и
     * связь именно такая: сколько зарядов, столько мемов и разблокировано.
     */
    public static void arm(MatchPlayer player) {
        List<String> loadout = normalize(player.getLoadout());
        int unlocked = Math.min(loadout.size(), WeaponRegistry.baseArsenal().getOrDefault("meme", 0));
        player.setArsenal(WeaponRegistry.baseArsenal());
        player.setLoadout(new ArrayList<>(loadout));
        player.setAvailable(new ArrayList<>(loadout.subList(0, unlocked)));
        player.setReserve(new ArrayList<>(loadout.subList(unlocked, loadout.size())));
        player.setRecycle(new ArrayList<>());
        player.setUsedMemeIds(new ArrayList<>());
        player.setCycleCursor(0);
        player.setSabotageCooldownUntil(0);
    }

    /** Замена мема в слоте: он меняется во всех трёх очередях сразу. */
    public static void replaceSlot(MatchPlayer player, int slotIndex, String nextMemeId) {
        MemeQueue queue = queue(player);
        String previous = queue.slot(slotIndex);
        queue.replaceSlot(slotIndex, nextMemeId);
        apply(player, queue);
        player.setUsedMemeIds(replace(player.getUsedMemeIds(), previous, nextMemeId));
    }

    private static List<String> replace(List<String> values, String from, String to) {
        List<String> result = new ArrayList<>();
        for (String value : values == null ? List.<String>of() : values) {
            result.add(from.equals(value) ? to : value);
        }
        return result;
    }
}
