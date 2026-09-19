package ru.hothat.game.domain;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Состояние партии — всё, чем распоряжается движок.
 *
 * <p>Это не строка базы и не документ: ни одной аннотации хранения здесь нет,
 * загрузку и запись делает {@code store}. Такой развод нужен затем, что
 * правила партии должны проверяться без базы вовсе — состояние собирается в
 * тесте руками, движку скармливается, результат сверяется.
 *
 * <p>Класс изменяемый намеренно. Ход правит десяток связанных полей сразу
 * (слово, мешок, счёт, отметку действия, часы), и неизменяемая запись
 * потребовала бы копировать все сорок полей на каждое угаданное слово —
 * дороже и, главное, длиннее в чтении, чем сам ход.
 *
 * <p>Времени и случайности внутри нет: и то и другое приходит снаружи
 * аргументами методов движка. Иначе «слово выпало» и «ход истёк» были бы
 * непроверяемы.
 */
@Getter
@Setter
public final class MatchState {

    // ───────────────────────── что за комната ─────────────────────────

    private final String roomId;
    /** Правила расширенного арсенала: он живёт только в режиме диверсий. */
    private final boolean sabotageMode;
    /** Тестовая комната: редкие награды и расширенное оружие в ней выключены. */
    private final boolean testRoom;
    private final boolean ranked;
    private final Set<String> testBotIds;
    /** Длительность хода по настройке комнаты, секунды. */
    private final double defaultTurnDurationSeconds;
    /**
     * Ходы владельца тестовой комнаты, как их считает прогон ботов: в какой
     * партии и который по счёту. Движок их не ведёт и не меняет — только
     * показывает экрану, который по ним решает, вести ли ход владельца самому.
     * Уедут вместе с прогоном в его область; здесь они, пока прогон пишет их
     * в строку комнаты.
     */
    private int testOwnerExplainerGameNumber;
    private int testOwnerExplainerTurnNumber;

    // ───────────────────────── течение партии ─────────────────────────

    private MatchPhase phase = MatchPhase.SETUP;
    private int gameNumber;

    /** Порядок ходов команд; индекс текущей команды — позиция в этом списке. */
    private List<String> teamOrder = new ArrayList<>();
    /** Замороженные на старте составы: партия играется ими, а не текущим списком комнаты. */
    private Map<String, List<String>> rosters = new LinkedHashMap<>();
    /** Замороженные на старте имена: игрок вышел — имя в протоколе осталось. */
    private Map<String, String> playerNames = new LinkedHashMap<>();
    private int currentTeamIndex;
    private String currentTeamId;

    // ───────────────────────── текущий ход ─────────────────────────

    private String turnId;
    private String explainerUid;
    private String explainerName;
    private String guesserUid;
    private String guesserName;
    /** Начало хода по серверным часам; {@code null} — ход не идёт или на паузе. */
    private Long turnStartedAtMs;
    /** Длительность именно этого хода: после паузы она равна остатку. */
    private Double turnDurationSeconds;
    /** Дедлайн от прошлой версии: читается, если нет начала хода. */
    private long legacyTurnEndsAt;
    private int currentTurnScore;
    private List<GuessedWord> guessedWords = new ArrayList<>();

    // ───────────────────────── шляпа ─────────────────────────

    private List<String> bag = new ArrayList<>();
    private String currentWord;
    private int wordsLeft;
    private String lastGuessedWord;
    private String lastSkippedWord;
    private String lastActionType;
    private String lastActionWord;
    private long lastActionAtMs;

    // ───────────────────────── апелляция ─────────────────────────

    private long appealEndsAt;
    private Map<String, List<String>> appealVotes = new LinkedHashMap<>();
    private LastTurn lastTurn;

    // ───────────────────────── пауза ─────────────────────────

    private boolean paused;
    private boolean hostPaused;
    private String pauseReason;
    private List<String> pauseMissingUids = new ArrayList<>();
    private List<String> pauseMissingNames = new ArrayList<>();
    private long pauseStartedAtMs;
    private long pausedTurnRemainingMs;
    private long pausedAppealRemainingMs;
    private TechnicalTermination termination;

    // ───────────────────────── диверсии ─────────────────────────

    private SabotageLocks locks = new SabotageLocks();
    private Map<String, ReplacementClip> clips = new LinkedHashMap<>();
    private SabotageEvent lastEvent;
    private List<SabotageEvent> recentEvents = new ArrayList<>();
    /** Сколько слов команда угадала за партию: по этому счёту идут редкие награды. */
    private Map<String, Integer> specialProgressByTeam = new LinkedHashMap<>();
    /** Кому из команды достанется следующая редкая награда: иначе они копятся у одного. */
    private Map<String, Integer> specialCursorByTeam = new LinkedHashMap<>();

    public MatchState(String roomId, boolean sabotageMode, boolean testRoom, boolean ranked,
                      Set<String> testBotIds, double defaultTurnDurationSeconds) {
        this.roomId = roomId;
        this.sabotageMode = sabotageMode;
        this.testRoom = testRoom;
        this.ranked = ranked;
        this.testBotIds = testBotIds == null ? Set.of() : Set.copyOf(testBotIds);
        this.defaultTurnDurationSeconds = defaultTurnDurationSeconds <= 0 ? 60 : defaultTurnDurationSeconds;
    }

    // ───────────────────────── состав партии ─────────────────────────

    /** Состав названной команды — по замороженным на старте спискам. */
    public List<String> rosterOf(String teamId) {
        List<String> roster = teamId == null ? null : rosters.get(teamId);
        return roster == null ? List.of() : List.copyOf(roster);
    }

    public List<String> activeRoster() {
        return rosterOf(currentTeamId);
    }

    /**
     * Все игроки партии — по составам команд, а не по списку в комнате.
     * Зашедший позже зритель игроком партии не становится.
     */
    public List<String> allPlayers() {
        Set<String> all = new LinkedHashSet<>();
        for (List<String> roster : rosters.values()) {
            all.addAll(roster);
        }
        return List.copyOf(all);
    }

    public boolean isPlayer(String uid) {
        return uid != null && allPlayers().contains(uid);
    }

    public boolean isTestBot(String uid) {
        return testRoom && testBotIds.contains(uid);
    }

    /** Живые игроки команды: тест-ботам награды не нужны, у них нет строки игрока. */
    public List<String> humanRosterOf(String teamId) {
        return rosterOf(teamId).stream().filter(uid -> !isTestBot(uid)).toList();
    }

    public String nameOf(String uid) {
        String name = playerNames.get(uid);
        if (name != null && !name.isBlank()) {
            return name;
        }
        String tail = uid == null || uid.length() <= 4 ? String.valueOf(uid) : uid.substring(uid.length() - 4);
        return "Участник " + tail;
    }

    /** Кто угадывает при этом объясняющем: в команде их двое, значит — второй. */
    public String partnerOf(String teamId, String explainerUid) {
        List<String> roster = rosterOf(teamId);
        for (String uid : roster) {
            if (!uid.equals(explainerUid)) {
                return uid;
            }
        }
        return null;
    }

    // ───────────────────────── удобства чтения ─────────────────────────

    public boolean sabotageAllowed() {
        return sabotageMode && !testRoom;
    }

    public int teamCount() {
        return teamOrder.size();
    }

    public boolean bagEmpty() {
        return bag.isEmpty();
    }
}
