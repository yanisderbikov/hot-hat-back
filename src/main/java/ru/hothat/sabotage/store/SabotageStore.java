package ru.hothat.sabotage.store;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import ru.hothat.common.identity.LegacyIdBridge;
import ru.hothat.config.ApiException;
import ru.hothat.game.domain.LoadoutRules;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Единственная дверь области диверсий в свои таблицы.
 *
 * <p>Наружу отдаёт записи, а не сущности: три класса {@code sabotage.store}
 * не публичны и в сигнатуры сценариев не попадают. Здесь же живёт перевод
 * uid ↔ uuid и memeId ↔ uuid: таблицы кластера объявляют и игрока, и мем как
 * uuid, а живая личность и живая библиотека мемов — это ещё строки. Знание об
 * этом стыке не должно растекаться по сценариям и уйдёт вместе с ним.
 *
 * <p>Ни одно чтение не ходит в базу в цикле: обоймы десяти участников подбора
 * — это два запроса, а не двадцать.
 *
 * <p><b>Что здесь НЕ живёт.</b> Правила — чья квота безлимитна, сколько партий
 * даётся даром, кого пускают в режим — принадлежат {@code sabotage.spi}. Этот
 * класс отвечает только на вопросы «что записано» и «запиши», и решений не
 * принимает: иначе правило «друг владельца играет без ограничений» пришлось
 * бы проверять из репозитория.
 */
@Component
@RequiredArgsConstructor
public class SabotageStore {

    private final SabotageEntitlements entitlements;
    private final SabotageGameGrants grants;
    private final DefaultLoadoutSlots slots;
    private final LegacyIdBridge ids;

    /**
     * Право играть с диверсиями как оно записано.
     *
     * @param unlimited безлимит: квота не считается вовсе
     * @param limit     сколько бесплатных партий полагается этой учётке
     * @param used      сколько из них израсходовано
     */
    public record Quota(boolean unlimited, int limit, int used) {

        /** Сколько осталось; у безлимита счёт не ведётся, и вопрос не имеет смысла. */
        public int remaining() {
            return Math.max(0, limit - used);
        }
    }

    // ───────────────────────────── чтение ─────────────────────────────

    /**
     * Квота учётки. Пусто — строки права ещё нет, то есть игрок сюда не
     * заходил ни разу; заводить её на чтении нельзя, чтение обязано оставаться
     * чтением.
     */
    public Optional<Quota> quota(String uid) {
        if (blank(uid)) {
            return Optional.empty();
        }
        return entitlements.findById(ids.playerId(uid)).map(SabotageStore::quota);
    }

    /** Стартовая обойма игрока по порядку слотов; пустая — обоймы нет. */
    public List<String> loadout(String uid) {
        if (blank(uid)) {
            return List.of();
        }
        return loadouts(List.of(uid)).getOrDefault(uid, List.of());
    }

    /**
     * Обоймы названных игроков разом: два запроса на любой список — слоты и
     * обратный перевод мемов.
     *
     * <p>Мем, которого нет в мосте идентификаторов, в обойму не попадает.
     * Молчаливая подстановка пустой строки нарисовала бы в арсенале слот без
     * мема и посчиталась бы за заряд — то есть пустила бы игрока в партию с
     * четырьмя мемами вместо пяти.
     */
    public Map<String, List<String>> loadouts(Collection<String> uids) {
        List<String> wanted = distinct(uids);
        if (wanted.isEmpty()) {
            return Map.of();
        }
        Map<UUID, String> byPlayerId = new LinkedHashMap<>();
        for (String uid : wanted) {
            byPlayerId.put(ids.playerId(uid), uid);
        }
        List<DefaultLoadoutSlot> rows =
                slots.findByPlayerIdInOrderByPlayerIdAscSlotIndexAsc(List.copyOf(byPlayerId.keySet()));
        List<UUID> memeKeys = new ArrayList<>();
        for (DefaultLoadoutSlot row : rows) {
            memeKeys.add(row.getMemeId());
        }
        Map<UUID, String> memeIds = ids.memeIds(memeKeys);
        Map<String, List<String>> result = new LinkedHashMap<>();
        for (DefaultLoadoutSlot row : rows) {
            String uid = byPlayerId.get(row.getPlayerId());
            String memeId = memeIds.get(row.getMemeId());
            if (uid == null || memeId == null) {
                continue;
            }
            result.computeIfAbsent(uid, key -> new ArrayList<>()).add(memeId);
        }
        return result;
    }

    // ───────────────────────────── запись ─────────────────────────────

    /**
     * Завести строку права, если её ещё нет, и вернуть её.
     *
     * <p>Возвращает, а не просто заводит: иначе у вызывающего появилась бы
     * ветка «завели, но не нашли», недостижимая по построению — вставка либо
     * прошла, либо столкнулась с уже существующей строкой. Предел бесплатных
     * партий приезжает из умолчания колонки, а не из константы в коде: ради
     * этого перенос и делался.
     *
     * <p>Строки не будет только у игрока, которого нет в {@code v2.user_account}:
     * внешний ключ не даст её завести, и это не «квота не тронута», а
     * «учётки не существует».
     */
    public Quota ensureQuota(String uid) {
        UUID playerId = ids.playerId(uid);
        entitlements.ensure(playerId);
        return entitlements.findById(playerId).map(SabotageStore::quota)
                .orElseThrow(() -> ApiException.of("PLAYER_NOT_FOUND", 404));
    }

    /**
     * Выдать безлимит навсегда.
     *
     * @return {@code true} — безлимит выдан именно сейчас
     */
    public boolean grantUnlimited(String uid) {
        return entitlements.grantUnlimited(ids.playerId(uid)) > 0;
    }

    /**
     * Списать одну бесплатную партию.
     *
     * <p>Два оператора и оба условные, поэтому исход различим на три случая:
     * <ul>
     *   <li>{@link Spending#SPENT} — партия засчитана впервые;
     *   <li>{@link Spending#REPEATED} — та же партия уже засчитана; клиент
     *       повторяет списание при каждом переподключении, и это не ошибка;
     *   <li>{@link Spending#EXHAUSTED} — квота исчерпана; журнальная запись,
     *       сделанная шагом раньше, откатывается вместе с транзакцией
     *       сценария, поэтому «списание есть, а счётчик не вырос» не остаётся.
     * </ul>
     * Порядок шагов обратный привычному — сперва журнал, потом счётчик — и
     * именно он делает повтор бесплатным: узнать «это уже считали» можно
     * только по ключу журнала, а спросив счётчик, мы бы списали второй раз.
     */
    public Spending spendFreeGame(String uid, String roomId, int gameNumber) {
        UUID playerId = ids.playerId(uid);
        if (grants.record(playerId, roomId, gameNumber) == 0) {
            return Spending.REPEATED;
        }
        return entitlements.spendFreeGame(playerId) > 0 ? Spending.SPENT : Spending.EXHAUSTED;
    }

    /** Исход списания бесплатной партии. */
    public enum Spending {
        SPENT, REPEATED, EXHAUSTED
    }

    /**
     * Заменить стартовую обойму целиком.
     *
     * <p>Возвращает то, что действительно записано: список нормализован той же
     * {@link LoadoutRules#normalize}, которой обойму читают, — иначе
     * сохранённое и прочитанное разошлись бы на повторяющемся меме.
     */
    public List<String> replaceLoadout(String uid, List<String> memeIds) {
        UUID playerId = ids.playerId(uid);
        List<String> loadout = LoadoutRules.normalize(memeIds);
        // Мосту нужны все мемы обоймы: без них запись пройдёт, а чтение
        // вернёт пустой слот — обратно md5 не считается.
        ids.rememberMemes(loadout);
        slots.clear(playerId);
        List<DefaultLoadoutSlot> rows = new ArrayList<>();
        for (int index = 0; index < loadout.size(); index++) {
            rows.add(DefaultLoadoutSlot.builder()
                    .playerId(playerId)
                    .slotIndex(index)
                    .memeId(ids.memeId(loadout.get(index)))
                    .updatedAt(Instant.now())
                    .build());
        }
        slots.saveAll(rows);
        return loadout;
    }

    private static Quota quota(SabotageEntitlement row) {
        return new Quota(Boolean.TRUE.equals(row.getUnlimited()),
                row.getFreeGamesLimit() == null ? 0 : row.getFreeGamesLimit(),
                row.getFreeGamesUsed() == null ? 0 : row.getFreeGamesUsed());
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private static List<String> distinct(Collection<String> values) {
        if (values == null) {
            return List.of();
        }
        return List.copyOf(new LinkedHashSet<>(values.stream().filter(value -> !blank(value)).toList()));
    }
}
