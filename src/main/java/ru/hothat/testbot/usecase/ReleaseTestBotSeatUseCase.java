package ru.hothat.testbot.usecase;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.hothat.config.ApiException;
import ru.hothat.config.HotHatUser;
import ru.hothat.model.room.Room;
import ru.hothat.model.room.RoomPlayer;
import ru.hothat.model.room.RoomTeam;
import ru.hothat.repository.GetterRoom;
import ru.hothat.repository.SaverRoom;
import ru.hothat.room.usecase.RoomAccessGuard;
import ru.hothat.testbot.api.dto.ReleasedTestBotResponseDTO;
import ru.hothat.util.Json;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * SCRUM-45: only the owner chooses when and WHICH bot to remove.
 * The team roster retains its old bot UID as an unoccupied seat marker;
 * a private room-only reservation holds its meme loadout for the invitee.
 * This runs under the same room-row write lock as EnterRoomUseCase.
 */
@Service
@RequiredArgsConstructor
public class ReleaseTestBotSeatUseCase {
    public static final String RESERVED_SLOTS = "__releasedBotSeats";

    private final RoomAccessGuard access;
    private final GetterRoom getterRoom;
    private final SaverRoom saverRoom;
    private final TestRoomFeatureGuard features;

    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public ReleasedTestBotResponseDTO run(HotHatUser owner, String roomId, String botUid) {
        features.requireEnabled();
        Room room = access.requireHostRoomForWrite(owner, roomId);
        if (!Boolean.TRUE.equals(room.getIsTestRoom())
                || !owner.uid().equals(room.getTestOwnerUid())) {
            throw ApiException.of("TEST_ROOM_ONLY", 403);
        }
        if (!"setup".equals(room.getPhase())) {
            throw ApiException.of("GAME_ALREADY_STARTED", 409);
        }
        if (room.getTestBotIds() == null || !room.getTestBotIds().contains(botUid)) {
            throw ApiException.of("TEST_BOT_SLOT_UNAVAILABLE", 409);
        }
        RoomPlayer bot = getterRoom.getPlayer(roomId, botUid)
                .orElseThrow(() -> ApiException.of("TEST_BOT_SLOT_UNAVAILABLE", 409));
        if (!Boolean.TRUE.equals(bot.getIsTestBot())) {
            throw ApiException.of("TEST_BOT_SLOT_UNAVAILABLE", 409);
        }
        RoomTeam team = getterRoom.getTeam(roomId, bot.getTeamId())
                .orElseThrow(() -> ApiException.of("TEST_BOT_SLOT_UNAVAILABLE", 409));
        if (team.getMemberUids() == null || !team.getMemberUids().contains(botUid)) {
            throw ApiException.of("TEST_BOT_SLOT_UNAVAILABLE", 409);
        }

        // Keep the team slot marker only in the existing JSONB roster.
        // The player row disappears immediately, so readiness remains false.
        Map<String,Object> runtime = new LinkedHashMap<>(
                room.getTestBotRuntime() == null ? Map.of() : room.getTestBotRuntime());
        Map<String,Object> reservations = new LinkedHashMap<>(Json.map(runtime.get(RESERVED_SLOTS)));
        Map<String,Object> reservation = new LinkedHashMap<>();
        reservation.put("teamId", team.getTeamId());
        reservation.put("memeLoadout", bot.getMemeLoadout() == null
                ? List.of() : new ArrayList<>(bot.getMemeLoadout()));
        reservations.put(botUid, reservation);
        runtime.remove(botUid);
        runtime.put(RESERVED_SLOTS, reservations);

        List<String> botIds = new ArrayList<>(room.getTestBotIds());
        botIds.remove(botUid);
        room.setTestBotIds(botIds);
        room.setTestBotRuntime(runtime);
        room.setLastActivityAt(System.currentTimeMillis());
        saverRoom.deletePlayer(roomId, botUid);
        saverRoom.save(room);
        return new ReleasedTestBotResponseDTO(roomId, botUid, team.getTeamId());
    }
}
