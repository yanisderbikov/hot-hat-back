package ru.hothat.room.usecase;

import lombok.RequiredArgsConstructor;
import ru.hothat.config.ApiException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.hothat.config.HotHatUser;
import ru.hothat.model.room.Room;
import ru.hothat.model.room.RoomPlayer;
import ru.hothat.model.room.RoomTeam;
import ru.hothat.profile.spi.PlayerCardPort;
import ru.hothat.repository.GetterRoom;
import ru.hothat.repository.SaverRoom;
import ru.hothat.room.api.dto.EnterRoomRequestDTO;
import ru.hothat.room.api.dto.RoomSeatResponseDTO;
import ru.hothat.room.domain.RoomAccessPolicy;
import ru.hothat.room.domain.RoomPhase;
import ru.hothat.room.domain.RoomPresence;
import ru.hothat.room.domain.RoomSeatingPolicy;
import ru.hothat.sabotage.spi.SabotageArmoryPort;
import ru.hothat.util.Divisions;
import ru.hothat.util.Json;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import ru.hothat.testbot.usecase.ReleaseTestBotSeatUseCase;

/**
 * Занять место игрока в комнате.
 *
 * <p>Сюда переехал весь допуск, который до сих пор выполнял браузер
 * ({@code joinRoom()}, {@code app-core.js:12258-12390}): дивизион, приватность,
 * вместимость, замороженный состав идущей партии. Клиент читал документ
 * комнаты, сам всё сверял и сам писал себе место — то есть каждое из этих
 * правил было уговором, а путь {@code rooms/*} в проверке прав на запись пуст
 * (находка A1). Правила теперь в домене, а место пишет сервер.
 *
 * <p>Сценарий идемпотентен: повторный вход чинит существующее место, а не
 * заводит второе. Это и есть возвращение по перезагрузке вкладки, ради
 * которого во фронте жила отдельная процедура
 * {@code resumeSetupRoomFromHash()}.
 *
 * <p>Комната берётся под замком записи до первого чтения состава: два
 * человека, входящие в последнее свободное место, встают в очередь, а не
 * садятся оба (находка B2).
 */
@Service
@RequiredArgsConstructor
public class EnterRoomUseCase {

    private final RoomAccessGuard roomAuthz;
    private final GetterRoom getterRoom;
    private final SaverRoom saverRoom;
    /** Обойма мемов принадлежит области диверсий, а не оболочке учётки. */
    private final SabotageArmoryPort armory;
    /** Имя, аватар и дивизион — у карточки игрока, а не в оболочке учётки. */
    private final PlayerCardPort cards;
    private final RoomSeats roomSeats;
    private final RoomProjections projections;

    @PreAuthorize("hasRole('USER')")
    @Transactional
    public RoomSeatResponseDTO run(HotHatUser user, String roomId, EnterRoomRequestDTO request) {
        Room room = roomAuthz.requireRoomForWrite(roomId);
        PlayerCardPort.Card card = cards.card(user.uid()).orElse(null);

        long now = System.currentTimeMillis();
        RoomPhase phase = RoomPhase.fromWire(room.getPhase());
        List<RoomPlayer> players = getterRoom.getPlayers(roomId);
        boolean seatExists = players.stream().anyMatch(player -> player.getUid().equals(user.uid()));
        String frozenTeamId = RoomSeats.frozenTeamOf(room, user.uid());

        // SCRUM-45: the host MUST manually release a bot first. The only
        // claimable slots are server-side reservations created by the admin
        // action; knowing a bot UID or an invite URL never evicts a live bot.
        // Both release and claim hold the same room-row write lock.
        boolean testRoom = Boolean.TRUE.equals(room.getIsTestRoom());
        boolean canClaimSeat = testRoom && phase.isSetup() && !seatExists;
        String targetBot = request == null || request.replaceTestBotUid() == null
                ? "" : request.replaceTestBotUid().trim();
        Map<String,Object> savedRuntime = Json.map(room.getTestBotRuntime());
        Map<String,Object> openSlots = Json.map(savedRuntime.get(ReleaseTestBotSeatUseCase.RESERVED_SLOTS));
        String claimedBotUid = "";
        RoomTeam freedTeam = null;
        Map<String,Object> claimData = Map.of();
        if (canClaimSeat) {
            String prefix = "testbot-" + roomId.substring(roomId.length() - 6) + "-";
            List<RoomTeam> teams = getterRoom.getTeams(roomId);
            for (Map.Entry<String,Object> entry : openSlots.entrySet()) {
                String botUid = entry.getKey();
                if (!targetBot.isEmpty() && !targetBot.equals(botUid)) continue;
                if (!botUid.startsWith(prefix)
                        || !botUid.substring(prefix.length()).matches("[1-9]")
                        || players.stream().anyMatch(p -> botUid.equals(p.getUid())))
                    continue;
                Map<String,Object> data = Json.map(entry.getValue());
                String teamId = Json.str(data.get("teamId"));
                RoomTeam team = teams.stream()
                        .filter(t -> teamId.equals(t.getTeamId())
                                && room.getTeamOrder().contains(teamId)
                                && t.getMemberUids() != null
                                && t.getMemberUids().contains(botUid))
                        .findFirst().orElse(null);
                if (team == null) continue;
                claimedBotUid = botUid;
                freedTeam = team;
                claimData = data;
                break;
            }
            if (freedTeam == null)
                throw ApiException.of("TEST_BOT_SLOT_UNAVAILABLE", 409);
        }
        // Temporary test-only loadout comes from the manually released bot
        // reservation. The invitee's own account and inventory stay unchanged.
        List<String> loadout = testRoom ? armory.loadout(user.uid()) : armory.requireLoadout(user.uid());
        if (testRoom && !armory.loadoutCharged(loadout)) {
            RoomPlayer currentSeat = players.stream()
                    .filter(p -> user.uid().equals(p.getUid())).findFirst().orElse(null);
            if (currentSeat != null && armory.loadoutCharged(currentSeat.getMemeLoadout())) {
                loadout = currentSeat.getMemeLoadout();
            } else {
                List<String> releasedLoadout = Json.list(claimData.get("memeLoadout"))
                        .stream().map(String::valueOf).toList();
                if (freedTeam != null && armory.loadoutCharged(releasedLoadout)) {
                    loadout = releasedLoadout;
                } else {
                    loadout = armory.requireLoadout(user.uid());
                }
            }
        }
        int othersAlive = (int) players.stream()
                .filter(player -> !player.getUid().equals(user.uid()))
                .filter(player -> RoomPresence.playerAlive(
                        Boolean.TRUE.equals(player.getIsTestBot()),
                        player.getLastSeenAt() == null ? 0L : player.getLastSeenAt(), now))
                .count();

        RoomAccessPolicy.RoomFacts facts = new RoomAccessPolicy.RoomFacts(
                phase,
                room.isClosed(),
                Boolean.TRUE.equals(room.getIsPrivate()) || testRoom,
                Boolean.TRUE.equals(room.getRanked()),
                Boolean.TRUE.equals(room.getManagedMatchmaking()),
                Divisions.normalize(room.getDivisionLanguage()),
                Divisions.normalize(RoomProjections.gameLanguage(room)),
                room.effectiveMaxPlayers());
        RoomAccessPolicy.Applicant applicant = new RoomAccessPolicy.Applicant(
                Divisions.normalize(card == null ? null : card.divisionLanguage()),
                seatExists,
                frozenTeamId != null,
                othersAlive);
        RoomAccessPolicy.refuseSeat(facts, applicant).ifPresent(refusal -> {
            throw RoomRefusals.of(refusal);
        });

        RoomSeats.Seated seated = roomSeats.seatPlayer(room, user.uid(), card, loadout,
                RoomSeats.Origin.self());
        if (freedTeam != null) {
            List<String> members = new ArrayList<>(freedTeam.getMemberUids());
            int at = members.indexOf(claimedBotUid);
            if (at < 0) throw ApiException.of("TEST_BOT_SLOT_UNAVAILABLE", 409);
            members.set(at, user.uid());
            freedTeam.setMemberUids(members);
            saverRoom.saveTeam(freedTeam);
            seated.player().setTeamId(freedTeam.getTeamId());
            saverRoom.savePlayer(seated.player());

            // Claim consumes its server-side reservation exactly once.
            Map<String,Object> runtime = new LinkedHashMap<>(savedRuntime);
            Map<String,Object> pending = new LinkedHashMap<>(openSlots);
            pending.remove(claimedBotUid);
            if (pending.isEmpty()) runtime.remove(ReleaseTestBotSeatUseCase.RESERVED_SLOTS);
            else runtime.put(ReleaseTestBotSeatUseCase.RESERVED_SLOTS, pending);
            room.setTestBotRuntime(runtime);
        } else if (phase.isSetup()) {
            boolean autoAssign = request != null && request.autoAssignTeamOrDefault();
            assignTeamIfNeeded(room, seated.player(), autoAssign, players, now);
        }
        // Живой вход отодвигает уборщика: комната, в которую только что вошли,
        // не должна попасть под общий проход как брошенная.
        room.setLastActivityAt(now);
        // Если хозяин вернулся в опустевшую тестовую комнату в течение 30 с,
        // сбрасываем ранее запущенный таймер удаления.
        if (Boolean.TRUE.equals(room.getIsTestRoom())) {
            room.setTestVacantSinceAt(0L);
        }
        saverRoom.save(room);

        return new RoomSeatResponseDTO(
                projections.room(room),
                projections.seat(seated.player(), now),
                seated.created(),
                frozenTeamId != null);
    }

    /**
     * Посадить вошедшего за стол, если садиться надо не руками.
     *
     * <p>Две причины и разный вес. Рейтинговый подбор закрепляет обоих
     * участников постоянной пары за одним слотом заранее — такое назначение
     * обязательно, и трогать его нельзя. Быстрый подбор просто просит место
     * посвободнее: это переезд {@code autoAssignMatchmadeTeam()}
     * ({@code app-core.js:12232}), где выбор делал браузер по своему снимку, и
     * двое вошедших одновременно садились в одну и ту же команду.
     */
    private void assignTeamIfNeeded(Room room, RoomPlayer player, boolean autoAssign,
                                    List<RoomPlayer> players, long nowMs) {
        String reserved = Json.str(Json.map(room.getRankedTeamAssignments()).get(player.getUid()));
        if (!reserved.isBlank()) {
            moveToTeam(room.getId(), player, reserved);
            return;
        }
        if (!autoAssign || (player.getTeamId() != null && !player.getTeamId().isBlank())) {
            return;
        }
        List<RoomTeam> teams = getterRoom.getTeams(room.getId());
        Map<String, Integer> aliveByTeam = new LinkedHashMap<>();
        for (RoomTeam team : teams) {
            aliveByTeam.put(team.getTeamId(), 0);
        }
        for (RoomPlayer seated : players) {
            String teamId = seated.getTeamId();
            if (teamId == null || seated.getUid().equals(player.getUid()) || !aliveByTeam.containsKey(teamId)) {
                continue;
            }
            boolean alive = RoomPresence.playerAlive(Boolean.TRUE.equals(seated.getIsTestBot()),
                    seated.getLastSeenAt() == null ? 0L : seated.getLastSeenAt(), nowMs);
            if (alive) {
                aliveByTeam.merge(teamId, 1, Integer::sum);
            }
        }
        RoomSeatingPolicy.autoSeat(aliveByTeam)
                .ifPresent(teamId -> moveToTeam(room.getId(), player, teamId));
    }

    /** Пересадка: место игрока и состав команды пишутся вместе. */
    private void moveToTeam(String roomId, RoomPlayer player, String teamId) {
        Optional<RoomTeam> target = getterRoom.getTeam(roomId, teamId);
        if (target.isEmpty()) {
            return;
        }
        roomSeats.removeFromTeams(roomId, player.getUid());
        RoomTeam team = target.get();
        List<String> members = new ArrayList<>(
                team.getMemberUids() == null ? List.of() : team.getMemberUids());
        if (!members.contains(player.getUid())) {
            members.add(player.getUid());
        }
        team.setMemberUids(members);
        saverRoom.saveTeam(team);
        player.setTeamId(teamId);
        saverRoom.savePlayer(player);
    }
}
