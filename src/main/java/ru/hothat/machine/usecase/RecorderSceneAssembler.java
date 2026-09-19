package ru.hothat.machine.usecase;

import org.springframework.stereotype.Component;
import ru.hothat.machine.api.dto.RecorderRoomStateView;
import ru.hothat.machine.api.dto.RecorderRosterPlayerView;
import ru.hothat.machine.api.dto.RecorderSabotageEventView;
import ru.hothat.machine.api.dto.RecorderSabotageType;
import ru.hothat.machine.api.dto.RecorderScenePlayerView;
import ru.hothat.machine.api.dto.RecorderTeamView;
import ru.hothat.model.room.Room;
import ru.hothat.model.room.RoomPlayer;
import ru.hothat.model.room.RoomTeam;
import ru.hothat.profile.api.dto.DivisionLanguage;
import ru.hothat.game.domain.WeaponRegistry;
import ru.hothat.team.api.dto.GameMode;
import ru.hothat.util.Divisions;
import ru.hothat.util.Json;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Собирает кадр и зеркало комнаты для рекордера.
 *
 * <p>Живёт отдельно от сценариев, потому что одни и те же проекции нужны
 * четырём адресам сразу — снаряжению, сцене, зеркалу и слову. Раньше все
 * четыре собирались приватными методами внутри одного {@code handle(...)} с
 * семью ветвлениями по флагам строки запроса, и форма ответа была описана
 * только телом метода.
 *
 * <p>Сущности комнаты сюда приходят уже прочитанными: сборщик не ходит в базу
 * и не может развести веер запросов, если его позовут в цикле.
 */
@Component
public class RecorderSceneAssembler {

    /** Столько последних диверсий держит в кадре страница записи. */
    private static final int RECENT_SABOTAGE_LIMIT = 24;

    /** Дольше двадцати секунд не длится ни один эффект: защита от кривого события. */
    private static final long MAX_SABOTAGE_DURATION_MS = 20000;

    private static final String DEFAULT_TEAM_NAME = "Команда";
    private static final String DEFAULT_PLAYER_NAME = "Игрок";

    public List<RecorderTeamView> teams(List<RoomTeam> teams) {
        List<RecorderTeamView> views = new ArrayList<>(teams.size());
        for (RoomTeam team : teams) {
            views.add(new RecorderTeamView(
                    team.getTeamId(),
                    Json.str(team.getName() == null ? DEFAULT_TEAM_NAME : team.getName(), 80),
                    team.getOrder() == null ? 0 : team.getOrder(),
                    team.getScore() == null ? 0 : team.getScore(),
                    team.getMemberUids() == null ? List.of() : List.copyOf(team.getMemberUids())));
        }
        return views;
    }

    /** Кадр: только то, что рисует плитку участника. */
    public List<RecorderScenePlayerView> scenePlayers(List<RoomPlayer> players) {
        List<RecorderScenePlayerView> views = new ArrayList<>(players.size());
        for (RoomPlayer player : players) {
            views.add(new RecorderScenePlayerView(
                    player.getUid(),
                    Json.str(player.getName() == null ? DEFAULT_PLAYER_NAME : player.getName(), 80),
                    Json.str(player.getTeamId()),
                    Boolean.TRUE.equals(player.getIsTestBot()),
                    blankToNull(player.getAvatarDataUrl()),
                    Boolean.TRUE.equals(player.getCameraEnabled()),
                    Boolean.TRUE.equals(player.getMicrophoneEnabled())));
        }
        return views;
    }

    /** Снаряжение: состав с арсеналом, аватары в кадре приезжают отдельно. */
    public List<RecorderRosterPlayerView> rosterPlayers(List<RoomPlayer> players) {
        List<RecorderRosterPlayerView> views = new ArrayList<>(players.size());
        for (RoomPlayer player : players) {
            views.add(new RecorderRosterPlayerView(
                    player.getUid(),
                    Json.str(player.getName() == null ? DEFAULT_PLAYER_NAME : player.getName(), 80),
                    Json.str(player.getTeamId()),
                    Boolean.TRUE.equals(player.getIsTestBot()),
                    player.getLastSeenAt() == null ? 0L : player.getLastSeenAt(),
                    Boolean.TRUE.equals(player.getCameraEnabled()),
                    Boolean.TRUE.equals(player.getMicrophoneEnabled()),
                    arsenal(player.getArsenal())));
        }
        return views;
    }

    public RecorderRoomStateView roomState(Room room, int gameNumber) {
        return new RecorderRoomStateView(
                room.getId(),
                blankToNull(room.getName()),
                room.getPhase(),
                gameNumber,
                GameMode.fromWire(room.getGameMode()),
                Boolean.TRUE.equals(room.getRanked()),
                Boolean.TRUE.equals(room.getIsPrivate()),
                Boolean.TRUE.equals(room.getIsTestRoom()),
                DivisionLanguage.fromWire(Divisions.normalize(room.getDivisionLanguage())),
                DivisionLanguage.fromWire(Divisions.normalize(room.getGameLanguage())),
                room.getTeamOrder() == null ? List.of() : List.copyOf(room.getTeamOrder()),
                stringLists(room.getTeamRosters()),
                strings(room.getGamePlayerNamesByUid()),
                blankToNull(room.getCurrentTeamId()),
                room.getCurrentTeamIndex() == null ? 0 : room.getCurrentTeamIndex(),
                blankToNull(Json.str(room.getCurrentWord(), 180)),
                room.getCurrentTurnScore() == null ? 0 : Math.max(0, room.getCurrentTurnScore()),
                room.getWordsLeft() == null ? 0 : Math.max(0, room.getWordsLeft()),
                room.getWordCount() == null ? 0 : Math.max(0, room.getWordCount()),
                blankToNull(room.getExplainerUid()),
                blankToNull(room.getExplainerName()),
                blankToNull(room.getGuesserUid()),
                blankToNull(room.getGuesserName()),
                blankToNull(room.getTurnId()),
                room.getTurnStartedAt() == null ? null : room.getTurnStartedAt().toEpochMilli(),
                room.getTurnDurationSeconds(),
                room.getTurnDuration() == null ? 0 : room.getTurnDuration(),
                room.getTurnEndsAt() == null ? 0L : room.getTurnEndsAt(),
                Json.maps(room.getTurnGuessedWords()),
                blankToNull(room.getLastGuessedWord()),
                blankToNull(room.getLastActionType()),
                blankToNull(room.getLastActionWord()),
                room.getLastActionAtMs() == null ? 0L : room.getLastActionAtMs(),
                room.getLastTurn(),
                room.getAppealEndsAt() == null ? 0L : room.getAppealEndsAt(),
                stringLists(room.getAppealVotes()),
                Boolean.TRUE.equals(room.getGamePaused()),
                Boolean.TRUE.equals(room.getHostPaused()),
                blankToNull(room.getPauseReason()),
                room.getPauseMissingUids() == null ? List.of() : List.copyOf(room.getPauseMissingUids()),
                room.getPauseMissingNames() == null ? List.of() : List.copyOf(room.getPauseMissingNames()),
                sabotageEvent(room.getSabotageEvent(), gameNumber),
                recentSabotage(room, gameNumber),
                Json.map(room.getSabotageLocks()),
                room.getTechnicalTermination(),
                Boolean.TRUE.equals(room.getRecordGame()),
                room.getTestBotIds() == null ? List.of() : List.copyOf(room.getTestBotIds()));
    }

    /**
     * Наружу отдаём только известные виды событий и только https-ссылки на
     * медиа: событие приходит из свободной структуры комнаты, и подставить в
     * него чужой адрес — значит подставить его в готовый MP4.
     */
    public RecorderSabotageEventView sabotageEvent(Map<String, Object> raw, int gameNumber) {
        if (raw == null || raw.isEmpty()) {
            return null;
        }
        RecorderSabotageType type = RecorderSabotageType.fromWire(Json.str(raw.get("type")));
        if (type == null) {
            return null;
        }
        long eventGameNumber = Json.num(raw.get("gameNumber"));
        if (eventGameNumber != 0 && eventGameNumber != gameNumber) {
            return null;
        }
        boolean meme = type == RecorderSabotageType.MEME;
        String attackerUid = Json.str(firstNonNull(
                raw.get("attackerUid"), raw.get("sourceUid"), raw.get("fromUid")), 160);
        return new RecorderSabotageEventView(
                Json.str(raw.get("id"), 120),
                type,
                attackerUid,
                Json.str(raw.get("attackerName"), 80),
                blankToNull(Json.str(raw.get("targetUid"), 160)),
                Json.num(firstNonNull(raw.get("createdAtMs"), raw.get("atMs"))),
                (int) eventGameNumber,
                Math.max(0, Math.min(MAX_SABOTAGE_DURATION_MS, Json.num(raw.get("durationMs")))),
                meme ? blankToNull(Json.str(raw.get("memeId"), 180)) : null,
                meme ? blankToNull(Json.str(raw.getOrDefault("memeTitle", "Мем"), 120)) : null,
                meme ? safeHttps(raw.get("memeSrc")) : null,
                meme ? safeHttps(raw.get("memePoster")) : null);
    }

    public List<RecorderSabotageEventView> recentSabotage(Room room, int gameNumber) {
        List<RecorderSabotageEventView> events = new ArrayList<>();
        for (Map<String, Object> item : Json.maps(room.getSabotageEventsRecent())) {
            RecorderSabotageEventView event = sabotageEvent(item, gameNumber);
            if (event != null) {
                events.add(event);
            }
        }
        return events.size() <= RECENT_SABOTAGE_LIMIT
                ? events : events.subList(events.size() - RECENT_SABOTAGE_LIMIT, events.size());
    }

    /** jsonb «ключ → список строк»: составы команд и голоса апелляции. */
    private static Map<String, List<String>> stringLists(Object raw) {
        Map<String, List<String>> out = new LinkedHashMap<>();
        Json.map(raw).forEach((key, value) -> out.put(key, Json.strings(value)));
        return out;
    }

    /** jsonb «ключ → строка»: имена участников партии. */
    private static Map<String, String> strings(Object raw) {
        Map<String, String> out = new LinkedHashMap<>();
        Json.map(raw).forEach((key, value) -> out.put(key, Json.str(value, 80)));
        return out;
    }

    private static Object firstNonNull(Object... values) {
        for (Object value : values) {
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private static String safeHttps(Object value) {
        String url = Json.str(value).trim();
        return url.toLowerCase().startsWith("https://") && url.length() < 2200 ? url : null;
    }

    /**
     * Боезапас игрока в каноническом виде.
     *
     * <p>Приведение делает каталог оружия области партии — тот же набор ключей
     * и те же базовые заряды, но без падения на ключе, которого в базовом
     * наборе нет. Считать боезапас здесь было бы вторым источником правды о
     * нём.
     */
    private static Map<String, Integer> arsenal(Map<String, Object> stored) {
        Map<String, Integer> counted = new LinkedHashMap<>();
        if (stored != null) {
            stored.forEach((key, value) -> counted.put(key, (int) Json.num(value)));
        }
        return WeaponRegistry.normalizeArsenal(counted);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
