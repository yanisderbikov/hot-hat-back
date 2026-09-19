package ru.hothat.room.domain;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Составы команд внутри комнаты: сколько их, кто в них садится и как их
 * тасует жеребьёвка.
 *
 * <p>Правила перенесены из браузера. Сегодня их держат четыре клиентские
 * транзакции — {@code addTeam()}, {@code deleteTeam()}, {@code joinTeam()},
 * {@code leaveTeam()} ({@code app-core.js:12511-12760}), — и каждая из них
 * заново решает, что команд не больше пяти, что в команде не больше двоих и
 * что менять состав можно только в наборе. Решает у себя: сервер до сих пор
 * не знал об этих правилах вовсе, и место в чужой команде занималось прямой
 * записью документа (находка A1 аудита).
 *
 * <p>Жеребьёвка сюда переехала с сервера, а не с клиента
 * ({@code GameServiceImpl.randomizeTeams}), и потеряла по дороге ровно одно —
 * генератор случайных чисел. Тасование делает вызывающий и передаёт готовые
 * перемешанные списки: правило рассадки не должно зависеть от источника
 * случайности, иначе его нельзя ни проверить, ни повторить.
 */
public final class RoomSeatingPolicy {

    /** Больше пяти команд партия не разыгрывает: очередь ходов станет вечной. */
    public static final int MAX_TEAMS = 5;

    /** Команда — это всегда пара: один объясняет, второй угадывает. */
    public static final int TEAM_SIZE = 2;

    private RoomSeatingPolicy() {
    }

    /** Почему состав менять нельзя. */
    public enum Refusal {
        /** Составы фиксируются на всю партию. */
        SETUP_ONLY,
        /** Команд уже пять. */
        TEAM_LIMIT_REACHED,
        /** Название занято другой командой этой же комнаты. */
        TEAM_NAME_TAKEN,
        /** В команде уже двое. */
        TEAM_IS_FULL,
        /** В команде остались живые игроки: сначала пусть выйдут. */
        TEAM_NOT_EMPTY,
        /** Команд меньше двух или больше пяти — жеребьёвке нечего раскладывать. */
        TEAMS_NOT_READY,
        /** Живых игроков нет вовсе. */
        NO_ACTIVE_PLAYERS
    }

    /**
     * Можно ли завести ещё одну команду.
     *
     * <p>Сравнение названий — регистронезависимое и по русской локали:
     * «Соколы» и «СОКОЛЫ» на экране неразличимы, и две такие команды означали
     * бы, что игрок не может понять, в какую из них он сел.
     */
    public static Optional<Refusal> refuseTeamCreation(RoomPhase phase, int existingTeams,
                                                       String name, List<String> existingNames) {
        if (!phase.isSetup()) {
            return Optional.of(Refusal.SETUP_ONLY);
        }
        if (existingTeams >= MAX_TEAMS) {
            return Optional.of(Refusal.TEAM_LIMIT_REACHED);
        }
        String key = nameKey(name);
        for (String existing : existingNames) {
            if (key.equals(nameKey(existing))) {
                return Optional.of(Refusal.TEAM_NAME_TAKEN);
            }
        }
        return Optional.empty();
    }

    /** Распустить команду можно только пустую: иначе игроки остались бы без места. */
    public static Optional<Refusal> refuseTeamDeletion(RoomPhase phase, int aliveMembers) {
        if (!phase.isSetup()) {
            return Optional.of(Refusal.SETUP_ONLY);
        }
        return aliveMembers > 0 ? Optional.of(Refusal.TEAM_NOT_EMPTY) : Optional.empty();
    }

    /**
     * Можно ли сесть в команду.
     *
     * <p>Считаются только живые: место человека, чья вкладка закрылась полчаса
     * назад, держать за ним нечестно, а вычищать его отдельным действием —
     * лишняя кнопка. Тот, кто уже сидит здесь, проходит всегда: повторное
     * нажатие не должно отвечать «команда полна».
     */
    public static Optional<Refusal> refuseTeamJoin(RoomPhase phase, int aliveMembers, boolean alreadyInTeam) {
        if (!phase.isSetup()) {
            return Optional.of(Refusal.SETUP_ONLY);
        }
        if (alreadyInTeam) {
            return Optional.empty();
        }
        return aliveMembers >= TEAM_SIZE ? Optional.of(Refusal.TEAM_IS_FULL) : Optional.empty();
    }

    /**
     * Можно ли выйти из команды.
     *
     * <p>Уходящий из комнаты выходит и из команды в любой фазе: не выпустить
     * его значило бы оставить в партии место, за которым никого нет. Просто
     * пересесть посреди партии нельзя — составы заморожены.
     */
    public static Optional<Refusal> refuseTeamLeave(RoomPhase phase, boolean leavingRoom) {
        if (leavingRoom || phase.isSetup()) {
            return Optional.empty();
        }
        return Optional.of(Refusal.SETUP_ONLY);
    }

    /** Итог жеребьёвки: кого куда посадили и кто остался за столом лишним. */
    public record Draw(Map<String, List<String>> membersByTeam, List<String> benched) {
    }

    /**
     * Разложить игроков по командам.
     *
     * <p>Каждый вызов — полностью новая жеребьёвка, включая уже рассаженных:
     * кнопка называется «перемешать», и половинчатое перемешивание, которое
     * трогает только свободных, выглядело бы как её поломка.
     *
     * <p>Оба списка приходят уже перемешанными. {@code shuffledSlots} — это
     * идентификаторы команд, повторённые {@link #TEAM_SIZE} раз и перетасованные:
     * так первая пара мест достаётся случайным командам, а не всегда первой.
     *
     * @param shuffledPlayers живые игроки в случайном порядке
     * @param shuffledSlots   места команд в случайном порядке, по два на команду
     */
    public static Draw draw(List<String> shuffledPlayers, List<String> shuffledSlots) {
        Map<String, List<String>> membersByTeam = new LinkedHashMap<>();
        for (String teamId : shuffledSlots) {
            membersByTeam.computeIfAbsent(teamId, id -> new ArrayList<>());
        }
        int seats = Math.min(shuffledPlayers.size(), shuffledSlots.size());
        for (int index = 0; index < seats; index++) {
            membersByTeam.get(shuffledSlots.get(index)).add(shuffledPlayers.get(index));
        }
        List<String> benched = new ArrayList<>(shuffledPlayers.subList(seats, shuffledPlayers.size()));
        return new Draw(membersByTeam, benched);
    }

    /** Готова ли комната к жеребьёвке. */
    public static Optional<Refusal> refuseDraw(RoomPhase phase, int teams, int alivePlayers) {
        if (!phase.isSetup()) {
            return Optional.of(Refusal.SETUP_ONLY);
        }
        if (teams < 2 || teams > MAX_TEAMS) {
            return Optional.of(Refusal.TEAMS_NOT_READY);
        }
        return alivePlayers == 0 ? Optional.of(Refusal.NO_ACTIVE_PLAYERS) : Optional.empty();
    }

    /**
     * Куда посадить игрока, пришедшего из подбора.
     *
     * <p>Переезд {@code autoAssignMatchmadeTeam()} ({@code app-core.js:12232}):
     * до сих пор новичок сам выбирал себе команду с наименьшим числом живых, и
     * два игрока, вошедшие одновременно, садились в одну и ту же — каждый по
     * своему снимку. Здесь выбор один и делается под замком строки комнаты.
     *
     * <p>Пусто — свободных мест нет: сажать некуда, и это не ошибка. Игрок
     * останется в комнате без команды и выберет её руками.
     */
    public static Optional<String> autoSeat(Map<String, Integer> aliveByTeam) {
        String chosen = null;
        int best = Integer.MAX_VALUE;
        for (Map.Entry<String, Integer> entry : aliveByTeam.entrySet()) {
            int taken = entry.getValue() == null ? 0 : entry.getValue();
            if (taken < TEAM_SIZE && taken < best) {
                best = taken;
                chosen = entry.getKey();
            }
        }
        return Optional.ofNullable(chosen);
    }

    private static String nameKey(String name) {
        return name == null ? "" : name.trim().toLowerCase(Locale.forLanguageTag("ru"));
    }
}
