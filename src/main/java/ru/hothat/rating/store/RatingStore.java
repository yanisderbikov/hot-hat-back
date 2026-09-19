package ru.hothat.rating.store;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Component;
import ru.hothat.common.identity.LegacyIdBridge;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Единственная дверь области рейтинга в свои таблицы.
 *
 * <p>Наружу отдаёт записи, а не сущности: пять классов {@code rating.store} не
 * публичны. Здесь же живёт перевод uid ↔ uuid, поэтому сценарии продолжают
 * говорить теми идентификаторами, которые стоят в ответах.
 *
 * <p>Чужих полей в таблицах сезона больше нет: ни имени команды, ни логотипа,
 * ни ников, ни состава. Всё это копии, устаревавшие в тот же миг, когда
 * команда меняла имя, а игрок — ник; проекции собираются на чтении.
 *
 * <p>Ни одно чтение не ходит в базу в цикле: страница таблицы — это доска,
 * сотня строк и один обратный перевод игроков.
 */
@Component
@RequiredArgsConstructor
public class RatingStore {

    /** Исходы зачёта; набор закрыт ограничением базы. */
    public static final String RECORDED = "recorded";
    public static final String ANNULLED = "annulled";

    private final RankingBoards boards;
    private final SeasonTeamStandings teamStandings;
    private final SeasonPlayerStandings playerStandings;
    private final MatchResultEvents events;
    private final MatchResultTeams eventTeams;
    private final LegacyIdBridge ids;

    // ───────────────────────────── доска сезона ─────────────────────────────

    /** Таблица сезона, если её уже заводили. Чтение доску не создаёт. */
    public Optional<BoardRow> board(int year, String season, String mode, String divisionLanguage) {
        return boards.findByYearAndSeasonAndModeAndDivisionLanguage(year, season, mode, divisionLanguage)
                .map(RatingStore::row);
    }

    /**
     * Таблица сезона; заводится, если её ещё нет.
     *
     * <p>Зовётся только с записи: взгляд на пустую таблицу доску больше не
     * создаёт. Иначе в базе оседала бы строка на каждый сезон, который
     * кто-нибудь однажды открыл в выпадающем списке.
     */
    public BoardRow ensureBoard(int year, String season, String mode, String divisionLanguage) {
        boards.insertIfAbsent(year, season, mode, divisionLanguage);
        return board(year, season, mode, divisionLanguage)
                .orElseThrow(() -> new IllegalStateException(
                        "Таблица сезона не завелась: " + year + "-" + season + "-" + mode + "-" + divisionLanguage));
    }

    // ───────────────────────────── таблицы ─────────────────────────────

    /** Страница таблицы команд: очки по убыванию, предел уезжает в базу. */
    public List<TeamStandingRow> teamStandings(long boardId, int limit) {
        if (limit <= 0) {
            return List.of();
        }
        List<TeamStandingRow> result = new ArrayList<>();
        for (SeasonTeamStanding row : teamStandings.findByBoardIdOrderByPointsDescTeamIdAsc(boardId, Limit.of(limit))) {
            result.add(row(row));
        }
        return result;
    }

    /** Показатели названных команд на этой доске — одним запросом на список. */
    public Map<String, TeamStandingRow> teamStandings(long boardId, Collection<String> teamIds) {
        List<UUID> wanted = uuids(teamIds);
        if (wanted.isEmpty()) {
            return Map.of();
        }
        Map<String, TeamStandingRow> result = new LinkedHashMap<>();
        for (SeasonTeamStanding row : teamStandings.findByBoardIdAndTeamIdIn(boardId, wanted)) {
            result.put(row.getTeamId().toString(), row(row));
        }
        return result;
    }

    /** Страница таблицы игроков; идентификаторы переводятся обратно пачкой. */
    public List<PlayerStandingRow> playerStandings(long boardId, int limit) {
        if (limit <= 0) {
            return List.of();
        }
        List<SeasonPlayerStanding> found =
                playerStandings.findByBoardIdOrderByPointsDescPlayerIdAsc(boardId, Limit.of(limit));
        List<UUID> players = new ArrayList<>(found.size());
        for (SeasonPlayerStanding row : found) {
            players.add(row.getPlayerId());
        }
        Map<UUID, String> uids = ids.playerUids(players);
        List<PlayerStandingRow> result = new ArrayList<>(found.size());
        for (SeasonPlayerStanding row : found) {
            String uid = uids.get(row.getPlayerId());
            if (uid == null || uid.isBlank()) {
                // Игрок, которого нет в мосте, — это строка таблицы без
                // человека: ни ника, ни ссылки на профиль у неё не будет.
                continue;
            }
            result.add(new PlayerStandingRow(
                    uid,
                    row.getTeamId() == null ? null : row.getTeamId().toString(),
                    row.getPoints(),
                    row.getGames(),
                    row.getWins()));
        }
        return result;
    }

    /** Личное положение игрока на этой доске; пусто — он ещё не играл. */
    public Optional<PlayerStandingRow> playerStanding(long boardId, String uid) {
        if (uid == null || uid.isBlank()) {
            return Optional.empty();
        }
        return playerStandings.findByBoardIdAndPlayerId(boardId, ids.playerId(uid))
                .map(row -> new PlayerStandingRow(
                        uid,
                        row.getTeamId() == null ? null : row.getTeamId().toString(),
                        row.getPoints(),
                        row.getGames(),
                        row.getWins()));
    }

    // ───────────────────────────── зачёт партии ─────────────────────────────

    /**
     * Занять партию под зачёт.
     *
     * @return {@code false} — партию уже засчитали, и очки второй раз не идут
     */
    public boolean claimMatch(String roomId, int gameNumber, long boardId, String outcome,
                              boolean technical, String reason, String recordedByUid) {
        ids.rememberPlayers(List.of(recordedByUid));
        return events.claim(roomId, gameNumber, boardId, outcome, technical, reason,
                ids.playerId(recordedByUid)) > 0;
    }

    /** Начислить команде очки за партию; отрицательные — это штраф. */
    public void addTeamResult(long boardId, String teamId, int points, int wins, int technicalForfeits) {
        UUID id = uuid(teamId);
        if (id != null) {
            teamStandings.addTeamResult(boardId, id, points, wins, technicalForfeits);
        }
    }

    /** Очки команды в таблице после зачёта; ноль — строки ещё нет. */
    public int teamPoints(long boardId, String teamId) {
        UUID id = uuid(teamId);
        return id == null ? 0 : teamStandings.findByBoardIdAndTeamId(boardId, id)
                .map(SeasonTeamStanding::getPoints).orElse(0);
    }

    /** Подтянуть личные очки игрока до командных. */
    public void syncPlayerWithTeam(long boardId, String uid, String teamId, int teamPoints, int wins) {
        UUID team = uuid(teamId);
        if (uid == null || uid.isBlank()) {
            return;
        }
        ids.rememberPlayers(List.of(uid));
        playerStandings.syncWithTeam(boardId, ids.playerId(uid), team, teamPoints, wins);
    }

    /**
     * Разбор партии по командам: место, начисление, виновность.
     *
     * <p>Пишется вместе с очками и в той же транзакции: без него «сколько
     * команда получила за эту партию» после следующего зачёта восстановить
     * нечем — таблица уже изменилась.
     */
    public void recordTeamBreakdown(String roomId, int gameNumber, String teamId,
                                    Integer place, int pointsDelta, boolean culprit) {
        UUID id = uuid(teamId);
        if (id == null) {
            return;
        }
        eventTeams.save(MatchResultTeam.builder()
                .roomId(roomId)
                .gameNumber(gameNumber)
                .teamId(id)
                .place(place)
                .pointsDelta(pointsDelta)
                .culprit(culprit)
                .build());
    }

    // ───────────────────────────── записи наружу ─────────────────────────────

    /** Доска так, как её видит сценарий: чемпион здесь — снимок, а не ссылка. */
    public record BoardRow(long boardId, int year, String season, String mode, String divisionLanguage,
                           String championTeamId, String championName) {
    }

    public record TeamStandingRow(String teamId, int points, int games, int wins, int technicalForfeits) {
    }

    public record PlayerStandingRow(String uid, String teamId, int points, int games, int wins) {
    }

    // ───────────────────────────── внутреннее ─────────────────────────────

    private static BoardRow row(RankingBoard board) {
        return new BoardRow(
                board.getBoardId(),
                board.getYear(),
                board.getSeason(),
                board.getMode(),
                board.getDivisionLanguage(),
                board.getChampionTeamId() == null ? null : board.getChampionTeamId().toString(),
                board.getChampionName());
    }

    private static TeamStandingRow row(SeasonTeamStanding row) {
        return new TeamStandingRow(
                row.getTeamId().toString(),
                row.getPoints(),
                row.getGames(),
                row.getWins(),
                row.getTechnicalForfeits());
    }

    /** Идентификатор команды приходит строкой; неразбираемый — это «нет такой». */
    private static UUID uuid(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(value.trim());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static List<UUID> uuids(Collection<String> values) {
        List<UUID> result = new ArrayList<>(values.size());
        for (String value : values) {
            UUID id = uuid(value);
            if (id != null && !result.contains(id)) {
                result.add(id);
            }
        }
        return result;
    }
}
