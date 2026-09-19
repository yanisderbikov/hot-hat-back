package ru.hothat.lobby.usecase;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.hothat.config.HotHatUser;
import ru.hothat.lobby.api.dto.LobbyArsenalView;
import ru.hothat.lobby.api.dto.LobbyPlayerView;
import ru.hothat.lobby.api.dto.LobbyRoomPhase;
import ru.hothat.lobby.api.dto.LobbyRoomPreviewResponseDTO;
import ru.hothat.lobby.api.dto.LobbySabotageEffectView;
import ru.hothat.lobby.api.dto.LobbyTeamView;
import ru.hothat.lobby.domain.LobbyVisibility;
import ru.hothat.model.room.Room;
import ru.hothat.model.room.RoomPlayer;
import ru.hothat.model.room.RoomTeam;
import ru.hothat.repository.GetterRoom;
import ru.hothat.util.Json;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Показать комнату крупным планом.
 *
 * <p>Заменяет четыре живые подписки: три на состав выбранной комнаты
 * ({@code home/home.js:88}) и одну на документ игрока
 * ({@code live-preview.js:34}). Три чтения вместо четырёх постоянных
 * подписок на каждого посетителя главной, причём главная меняет показываемую
 * комнату каждые тридцать секунд — то есть каждые полминуты браузер снимал
 * три подписки и заводил три новые.
 *
 * <p>Подписки на зрителей среди них нет: она существовала, но её результат
 * не рисовался нигде. Здесь её просто не стало.
 *
 * <p>Текущее слово наружу не выходит. Сегодня это правило держится на том,
 * что клиент сам не показывает поле, которое ему прислали; здесь оно держится
 * на форме ответа — прислать нечего.
 */
@Service
@RequiredArgsConstructor
public class GetRoomPreviewUseCase {

    /** Сколько человек одновременно сидит за столом: объясняющий и отгадывающий. */
    private static final int SEATS_AT_TABLE = 2;

    /** Запас на анимацию: столько эффект ещё «живой» после конца действия. */
    private static final long EFFECT_TAIL_MS = 800;

    private final GetterRoom getterRoom;
    private final RoomPreviewAccess access;

    @PreAuthorize("hasAnyRole('GUEST','USER')")
    @Transactional(readOnly = true)
    public LobbyRoomPreviewResponseDTO run(HotHatUser user, String roomId) {
        Room room = access.requireWatchable(roomId, user == null ? null : user.uid());
        long now = System.currentTimeMillis();

        List<RoomPlayer> alive = getterRoom.getPlayers(roomId).stream()
                .filter(player -> LobbyVisibility.playerAlive(
                        Boolean.TRUE.equals(player.getIsTestBot()),
                        player.getLastSeenAt() == null ? 0L : player.getLastSeenAt(),
                        now))
                .toList();
        List<RoomTeam> teams = getterRoom.getTeams(roomId);

        return new LobbyRoomPreviewResponseDTO(
                room.getId(),
                LobbyRooms.name(room),
                LobbyRoomPhase.live(room.getPhase()).orElse(LobbyRoomPhase.SETUP),
                LobbyRooms.mode(room),
                Boolean.TRUE.equals(room.getRanked()),
                LobbyRooms.gameLanguage(room),
                room.effectiveMaxPlayers(),
                blankToNull(room.getCurrentTeamId()),
                seatedUids(room, alive),
                blankToNull(room.getExplainerUid()),
                blankToNull(room.getExplainerName()),
                blankToNull(room.getGuesserUid()),
                blankToNull(room.getLastGuessedWord()),
                teams.stream().map(GetRoomPreviewUseCase::team).toList(),
                alive.stream().map(GetRoomPreviewUseCase::player).toList(),
                effect(room.getSabotageEvent(), now));
    }

    /**
     * Кто сейчас за столом.
     *
     * <p>Сначала состав хода, записанный комнатой: он назван явно и переживает
     * смену команд. Его нет — берём игроков ходящей команды, как это делал
     * клиент запасным путём. Обе ветки обрезаются двумя местами: за столом
     * сидят двое, и третий в списке означал бы ошибку рассадки, а не третью
     * плитку в превью.
     */
    private static List<String> seatedUids(Room room, List<RoomPlayer> alive) {
        String currentTeamId = room.getCurrentTeamId();
        if (currentTeamId == null || currentTeamId.isBlank()) {
            return List.of();
        }
        Map<String, Object> rosters = room.getTeamRosters() == null ? Map.of() : room.getTeamRosters();
        List<String> roster = Json.strings(rosters.get(currentTeamId));
        if (!roster.isEmpty()) {
            return roster.stream().limit(SEATS_AT_TABLE).toList();
        }
        List<String> byTeam = new ArrayList<>();
        for (RoomPlayer player : alive) {
            if (currentTeamId.equals(player.getTeamId()) && byTeam.size() < SEATS_AT_TABLE) {
                byTeam.add(player.getUid());
            }
        }
        return List.copyOf(byTeam);
    }

    private static LobbyTeamView team(RoomTeam team) {
        return new LobbyTeamView(
                team.getTeamId(),
                blankToNull(team.getName()),
                team.getOrder() == null ? 0 : team.getOrder(),
                team.getScore() == null ? 0 : team.getScore(),
                team.getMemberUids() == null ? List.of() : List.copyOf(team.getMemberUids()));
    }

    private static LobbyPlayerView player(RoomPlayer player) {
        return new LobbyPlayerView(
                player.getUid(),
                blankToNull(player.getName()),
                blankToNull(player.getAvatarDataUrl()),
                blankToNull(player.getTeamId()),
                Boolean.TRUE.equals(player.getIsTestBot()),
                arsenal(player.getArsenal()));
    }

    /**
     * Голосовых эффектов по умолчанию четыре, остальных — ноль.
     *
     * <p>Числа не выдуманы: так их читает превью
     * ({@code live-preview.js:19}), и разойтись с ним значило бы показать
     * зрителю не тот боезапас, который увидит игрок.
     */
    private static LobbyArsenalView arsenal(Map<String, Object> arsenal) {
        Map<String, Object> raw = arsenal == null ? Map.of() : arsenal;
        return new LobbyArsenalView(
                (int) Math.max(0, Json.num(raw.get("meme"), 0)),
                (int) Math.max(0, Json.num(raw.get("tomato"), 0)),
                (int) Math.max(0, Json.num(raw.get("crocodile"), 0)),
                (int) Math.max(0, Json.num(raw.get("voice"), 4)));
    }

    /**
     * Диверсия, которая ещё действует.
     *
     * <p>Отгоревшую не отдаём вовсе: снимок читают в произвольный момент, и
     * событие часовой давности означало бы помидор, прилетевший в пустоту.
     * Проверка возраста в клиенте при этом остаётся — она нужна ему для живого
     * канала, где событие приходит один раз и может застать вкладку спящей.
     */
    private static LobbySabotageEffectView effect(Map<String, Object> event, long nowMs) {
        if (event == null || event.isEmpty()) {
            return null;
        }
        String id = Json.str(event.get("id"));
        if (id.isEmpty()) {
            return null;
        }
        long createdAtMs = Json.num(event.get("createdAtMs"), 0);
        long durationMs = Math.max(0, Json.num(event.get("durationMs"), 0));
        if (nowMs > createdAtMs + durationMs + EFFECT_TAIL_MS) {
            return null;
        }
        return new LobbySabotageEffectView(
                id,
                Json.str(event.get("type")),
                blankToNull(Json.str(event.get("targetUid"))),
                createdAtMs,
                durationMs);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
