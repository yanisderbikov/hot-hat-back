package ru.hothat.rating.usecase;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.hothat.config.ApiException;
import ru.hothat.config.HotHatUser;
import ru.hothat.model.room.Room;
import ru.hothat.model.room.RoomTeam;
import ru.hothat.rating.api.dto.MatchResultOutcome;
import ru.hothat.rating.api.dto.SubmitMatchResultRequestDTO;
import ru.hothat.rating.api.dto.SubmittedMatchResultResponseDTO;
import ru.hothat.rating.store.RatingStore;
import ru.hothat.repository.GetterRoom;
import ru.hothat.team.spi.RankedTeamPort;
import ru.hothat.util.Divisions;
import ru.hothat.util.Json;
import ru.hothat.util.Seasons;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Зачесть результат рейтинговой партии.
 *
 * <p>Идемпотентность держит база, а не проверка перед записью. Раньше «уже
 * засчитано?» было чтением, и два клиента, дожавшие кнопку одновременно,
 * начисляли очки дважды. Здесь партия сперва занимается вставкой в
 * {@code match_result_event}, и очки считает только тот, чья вставка прошла.
 *
 * <p>Права шире, чем в плане: там стоит {@code @gameAuthz.isRankedParticipant},
 * но пакета предикатов безопасности ещё нет. Пока его нет, «я играл в этой
 * партии» остаётся инвариантом внутри сценария: составы команд комнаты
 * читаются, и спрашивающего среди них обязано быть. Проверка должна быть на
 * сервере — иначе любой вошедший начислял бы очки чужой команде.
 */
@Service
@RequiredArgsConstructor
public class RecordMatchResultUseCase {

    /** Очки за место; всё, что ниже пятого, стоит 2 очка. */
    private static final Map<Integer, Integer> PLACE_POINTS = Map.of(1, 100, 2, 35, 3, 20, 4, 10, 5, 5);
    private static final int DEFAULT_PLACE_POINTS = 2;

    /** Штраф за техническое поражение растёт: 15 → 30 → 50. */
    private static final int[] TECHNICAL_PENALTIES = {15, 30, 50};

    /** Почему партия аннулирована: связь пропала у всех разом. */
    private static final String MASS_DISCONNECT = "mass_disconnect";

    private final GetterRoom getterRoom;
    private final RatingStore ratings;
    private final RankedTeamPort teams;

    @PreAuthorize("hasRole('USER')")
    @Transactional
    public SubmittedMatchResultResponseDTO run(HotHatUser user, SubmitMatchResultRequestDTO request) {
        Room room = getterRoom.getById(request.roomId())
                .orElseThrow(() -> ApiException.of("ROOM_NOT_FOUND", 404));
        if (!"finished".equals(room.getPhase()) || !Boolean.TRUE.equals(room.getRanked())) {
            throw ApiException.of("NOT_RANKED_RESULT", 409);
        }
        List<RoomTeam> roomTeams = getterRoom.getTeams(room.getId()).stream()
                .filter(team -> team.getRankedTeamId() != null)
                .toList();
        if (roomTeams.size() < 2) {
            throw ApiException.of("RANKED_TEAMS_MISSING", 409);
        }
        if (roomTeams.stream().noneMatch(team -> team.getMemberUids().contains(user.uid()))) {
            throw ApiException.of("PLAYER_NOT_FOUND", 403);
        }

        Seasons.Season season = Seasons.now();
        String mode = Seasons.MODES.contains(room.getGameMode()) ? room.getGameMode() : "sabotage";
        String division = Divisions.normalize(room.getDivisionLanguage());
        String rankingId = Seasons.rankingId(season.year(), season.season(), mode, division);
        // Доска заводится здесь и только здесь: её создаёт зачёт, а не взгляд
        // на пустую таблицу.
        RatingStore.BoardRow board = ratings.ensureBoard(season.year(), season.season(), mode, division);
        int gameNumber = room.getGameNumber() == null ? 0 : room.getGameNumber();

        Map<String, Object> termination = room.getTechnicalTermination();
        boolean technical = termination != null;
        // Массовый обрыв связи никого не наказывает: партия аннулируется целиком.
        if (technical && Json.bool(termination.get("noPenalty"))) {
            if (!ratings.claimMatch(room.getId(), gameNumber, board.boardId(),
                    RatingStore.ANNULLED, true, MASS_DISCONNECT, user.uid())) {
                return alreadyRecorded();
            }
            // Аннулирование не разбирает партию по командам: ни мест, ни
            // начислений у неё нет.
            return new SubmittedMatchResultResponseDTO(
                    MatchResultOutcome.ANNULLED, rankingId, null, null, List.of());
        }

        Set<String> missingUids = new HashSet<>(
                termination == null ? List.of() : Json.strings(termination.get("missingUids")));
        Set<String> culpritIds = new LinkedHashSet<>();
        if (technical) {
            for (RoomTeam team : roomTeams) {
                if (team.getMemberUids().stream().anyMatch(missingUids::contains)) {
                    culpritIds.add(team.getRankedTeamId());
                }
            }
        }

        if (!ratings.claimMatch(room.getId(), gameNumber, board.boardId(),
                RatingStore.RECORDED, technical, null, user.uid())) {
            return alreadyRecorded();
        }

        List<String> teamIds = new ArrayList<>(roomTeams.size());
        for (RoomTeam team : roomTeams) {
            teamIds.add(team.getRankedTeamId());
        }
        // Прошлые техпоражения нужны до начисления: от них зависит размер
        // штрафа. Читаются одной пачкой, а не по команде в цикле.
        Map<String, RatingStore.TeamStandingRow> before = ratings.teamStandings(board.boardId(), teamIds);

        awardPlaces(board.boardId(), room.getId(), gameNumber, roomTeams, culpritIds);
        punishCulprits(board.boardId(), room.getId(), gameNumber, roomTeams, culpritIds, before);
        syncPlayers(board.boardId(), room.getId(), roomTeams, culpritIds, teamIds);

        return new SubmittedMatchResultResponseDTO(
                MatchResultOutcome.RECORDED, rankingId, division, technical, new ArrayList<>(culpritIds));
    }

    /**
     * Места и начисления. Команды с равным счётом делят сумму очков за
     * занятые ими места — иначе жребий решал бы, кому достанется первое.
     */
    private void awardPlaces(long boardId, String roomId, int gameNumber,
                             List<RoomTeam> roomTeams, Set<String> culpritIds) {
        List<RoomTeam> eligible = new ArrayList<>(roomTeams.stream()
                .filter(team -> !culpritIds.contains(team.getRankedTeamId()))
                .toList());
        eligible.sort((a, b) -> Integer.compare(b.getScore(), a.getScore()));

        List<List<RoomTeam>> groups = new ArrayList<>();
        Integer lastScore = null;
        for (RoomTeam team : eligible) {
            if (lastScore != null && lastScore.equals(team.getScore())) {
                groups.get(groups.size() - 1).add(team);
            } else {
                groups.add(new ArrayList<>(List.of(team)));
                lastScore = team.getScore();
            }
        }

        int place = 1;
        for (List<RoomTeam> group : groups) {
            int sum = 0;
            for (int i = 0; i < group.size(); i++) {
                sum += PLACE_POINTS.getOrDefault(place + i, DEFAULT_PLACE_POINTS);
            }
            int points = Math.round((float) sum / group.size());
            for (RoomTeam team : group) {
                ratings.addTeamResult(boardId, team.getRankedTeamId(), points, place == 1 ? 1 : 0, 0);
                ratings.recordTeamBreakdown(roomId, gameNumber, team.getRankedTeamId(), place, points, false);
            }
            place += group.size();
        }
    }

    /**
     * Виновные в обрыве связи. Места они не занимают — им начисляется штраф,
     * и растёт он с каждым техническим поражением.
     */
    private void punishCulprits(long boardId, String roomId, int gameNumber, List<RoomTeam> roomTeams,
                                Set<String> culpritIds, Map<String, RatingStore.TeamStandingRow> before) {
        for (RoomTeam team : roomTeams) {
            String teamId = team.getRankedTeamId();
            if (!culpritIds.contains(teamId)) {
                continue;
            }
            RatingStore.TeamStandingRow row = before.get(teamId);
            int forfeits = row == null ? 0 : Math.max(0, row.technicalForfeits());
            int penalty = TECHNICAL_PENALTIES[Math.min(forfeits, TECHNICAL_PENALTIES.length - 1)];
            ratings.addTeamResult(boardId, teamId, -penalty, 0, 1);
            ratings.recordTeamBreakdown(roomId, gameNumber, teamId, null, -penalty, true);
        }
    }

    /**
     * Личные очки — разница командных с прошлой синхронизации. Командные
     * читаются уже после начисления, одной пачкой: считать их «как было плюс
     * дельта» значило бы завести второе место, где известна та же сумма.
     */
    private void syncPlayers(long boardId, String roomId, List<RoomTeam> roomTeams,
                             Set<String> culpritIds, List<String> teamIds) {
        Map<String, RatingStore.TeamStandingRow> after = ratings.teamStandings(boardId, teamIds);
        Map<String, RankedTeamPort.TeamSummary> cards = teams.teams(teamIds);
        int topScore = roomTeams.stream()
                .filter(team -> !culpritIds.contains(team.getRankedTeamId()))
                .mapToInt(RoomTeam::getScore)
                .max()
                .orElse(0);

        for (RoomTeam team : roomTeams) {
            String teamId = team.getRankedTeamId();
            RankedTeamPort.TeamSummary card = cards.get(teamId);
            if (card == null) {
                // Команда распалась между партией и зачётом: командную строку
                // сезона мы уже написали (она историческая и переживает
                // роспуск), а состава, которому начислять личные очки, нет.
                continue;
            }
            RatingStore.TeamStandingRow row = after.get(teamId);
            int teamPoints = row == null ? 0 : row.points();
            boolean won = !culpritIds.contains(teamId) && team.getScore() == topScore;
            for (String uid : card.memberUids()) {
                ratings.syncPlayerWithTeam(boardId, uid, teamId, teamPoints, won ? 1 : 0);
            }
        }
    }

    /**
     * Повторный зачёт. Ни дивизиона, ни признака технического завершения он
     * не называет: об этом уже сказал первый, и повторять чужой ответ значило
     * бы делать вид, что очки посчитал этот вызов.
     */
    private static SubmittedMatchResultResponseDTO alreadyRecorded() {
        return new SubmittedMatchResultResponseDTO(
                MatchResultOutcome.ALREADY_RECORDED, null, null, null, List.of());
    }
}
