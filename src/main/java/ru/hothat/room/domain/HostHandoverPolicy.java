package ru.hothat.room.domain;

import java.util.List;

/**
 * Продвижение хозяйства комнаты: когда права уходят от бездействующего
 * хозяина и когда возвращаются к вернувшемуся.
 *
 * <p>Правило перенесено из {@code GameServiceImpl.setupHostWatch} и очищено от
 * двух дефектов, названных аудитом. Первый — форма ответа: тот метод отдавал
 * пять несовместимых карт, и клиент различал их по наличию ключей
 * {@code restored}/{@code transferred}/{@code remainingMs} (замечание C6).
 * Здесь исход назван перечислением, и «нет ключа» перестаёт что-либо значить.
 * Второй — права: {@code setupHostWatch} был единственным методом среза без
 * проверки участия, хотя писал {@code room.createdBy} (находка A6). Сама
 * проверка живёт в сценарии, но выражать её решением политики нельзя было бы,
 * если бы политика продолжала решать за постороннего.
 *
 * <p>Кто именно опрашивает сторожа, политике безразлично: сегодня это делает
 * ровно один не-хозяин из состава ({@code app-core.js:1078}), завтра может
 * делать планировщик. Решение зависит только от часов и состава.
 *
 * <p>Класс без Spring, без базы и без собственных часов: время приходит
 * аргументом. Иначе «через три минуты бездействия» нельзя было бы проверить,
 * не прождав трёх минут.
 */
public final class HostHandoverPolicy {

    /**
     * Сколько хозяин может ничего не делать, пока комната укомплектована.
     *
     * <p>Три минуты — то же значение, что сегодня в
     * {@code GameServiceImpl.HOST_INACTIVITY_MS} и в двух местах фронтенда.
     * Отсчёт идёт только при полном составе: пока комната собирается,
     * бездействие хозяина никому не мешает.
     */
    public static final long IDLE_LIMIT_MS = 3 * 60 * 1000;

    /**
     * Наименьший состав, при котором сторож вообще включается.
     *
     * <p>Комнату на двоих отнимать не за что: там просто некому играть.
     */
    public static final int MIN_TARGET_PLAYERS = 4;

    private HostHandoverPolicy() {
    }

    /**
     * Состояние комнаты глазами сторожа.
     *
     * @param phase             фаза комнаты
     * @param privateRoom       приватная ли комната
     * @param capacity          на сколько игроков комната рассчитана
     * @param currentHostUid    кто хозяин сейчас
     * @param previousHostUid   кого вернуть, если он появится; пусто — некого
     * @param lastActivityAtMs  когда хозяин последний раз что-то делал; 0 — ни разу
     * @param aliveUids         живые участники в порядке рассадки
     */
    public record Facts(RoomPhase phase,
                        boolean privateRoom,
                        int capacity,
                        String currentHostUid,
                        String previousHostUid,
                        long lastActivityAtMs,
                        List<String> aliveUids) {
    }

    /**
     * Решение сторожа.
     *
     * @param outcome     что произошло
     * @param newHostUid  новый хозяин; заполнено только у {@code RESTORED} и {@code TRANSFERRED}
     * @param remainingMs сколько осталось до передачи; заполнено только у {@code COUNTDOWN}
     * @param alivePlayers сколько живых участников насчитали
     * @param target      при каком составе сторож включается
     * @param resetActivityClock  отсчёт нужно начать заново — хозяин сменился либо часы ещё не заводили
     */
    public record Decision(HostHandoverOutcome outcome,
                           String newHostUid,
                           Long remainingMs,
                           int alivePlayers,
                           int target,
                           boolean resetActivityClock) {
    }

    /**
     * Решить, что делать с хозяйством.
     *
     * <p>Порядок веток и есть правило. Возврат прежнего хозяина проверяется
     * раньше отсчёта: человек, у которого отняли комнату по бездействию, а он
     * тут же вернулся, должен получить её обратно, не дожидаясь, пока состав
     * снова окажется полным. Возврат возможен только один раз — передача по
     * бездействию стирает {@code previousHostUid} и становится окончательной,
     * иначе комната ходила бы по кругу между двумя людьми.
     */
    public static Decision decide(Facts facts, long nowMs) {
        int target = Math.max(MIN_TARGET_PLAYERS, facts.capacity());
        int alive = facts.aliveUids().size();
        if (!facts.phase().isSetup()) {
            return new Decision(HostHandoverOutcome.NOT_APPLICABLE, null, null, alive, target, false);
        }
        if (facts.privateRoom()) {
            return new Decision(HostHandoverOutcome.PRIVATE_ROOM, null, null, alive, target, false);
        }
        String previous = facts.previousHostUid() == null ? "" : facts.previousHostUid();
        String current = facts.currentHostUid() == null ? "" : facts.currentHostUid();
        if (!previous.isEmpty() && !previous.equals(current) && facts.aliveUids().contains(previous)) {
            return new Decision(HostHandoverOutcome.RESTORED, previous, null, alive, target, true);
        }
        if (alive != target) {
            return new Decision(HostHandoverOutcome.WAITING_FOR_PLAYERS, null, null, alive, target, false);
        }
        if (facts.lastActivityAtMs() <= 0) {
            // Часы ещё не заводили: комната только что укомплектовалась.
            // Считать это трёхминутным бездействием было бы неверно — хозяин
            // не успел бы ничего сделать.
            return new Decision(HostHandoverOutcome.COUNTDOWN, null, IDLE_LIMIT_MS, alive, target, true);
        }
        long remainingMs = facts.lastActivityAtMs() + IDLE_LIMIT_MS - nowMs;
        if (remainingMs > 0) {
            return new Decision(HostHandoverOutcome.COUNTDOWN, null, remainingMs, alive, target, false);
        }
        String candidate = null;
        for (String uid : facts.aliveUids()) {
            if (!uid.equals(current)) {
                candidate = uid;
                break;
            }
        }
        if (candidate == null) {
            return new Decision(HostHandoverOutcome.NO_CANDIDATE, null, null, alive, target, false);
        }
        return new Decision(HostHandoverOutcome.TRANSFERRED, candidate, null, alive, target, true);
    }
}
