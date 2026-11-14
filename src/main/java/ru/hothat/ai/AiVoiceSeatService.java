package ru.hothat.ai;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.hothat.config.ApiException;
import ru.hothat.config.HotHatUser;
import ru.hothat.model.room.Room;
import ru.hothat.model.room.RoomPlayer;
import ru.hothat.model.room.RoomTeam;
import ru.hothat.repository.GetterRoom;
import ru.hothat.repository.SaverRoom;
import ru.hothat.room.store.RoomRows;
import ru.hothat.media.spi.MemeLibraryPort;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Transactional roster management. No LiveKit/Inference call is made here. */
@Service
@RequiredArgsConstructor
public class AiVoiceSeatService {
    private final RoomRows roomRows;
    private final GetterRoom getterRoom;
    private final SaverRoom saverRoom;
    private final MemeLibraryPort memeLibrary;

    private Room requireHost(HotHatUser user, String roomId) {
        Room room = roomRows.lock(roomId).orElseThrow(() -> ApiException.of("ROOM_NOT_FOUND",404));
        if (!user.uid().equals(room.getCreatedBy()) && !user.owner()) {
            throw ApiException.of("AI_ROOM_HOST_ONLY",403);
        }
        return room;
    }

    /** Backward-compatible default persona for previously released clients. */
    public Map<String,Object> add(HotHatUser user, String roomId, String teamId) {
        return add(user,roomId,teamId,"rublisha");
    }

    /** Server-authoritative cast selection; only the SITE owner may create an AI-only pair. */
    @Transactional
    public Map<String,Object> add(HotHatUser user, String roomId, String teamId, String characterId) {
        AiVoiceCharacters.Character character=AiVoiceCharacters.byId(characterId);
        Room room = requireHost(user, roomId);
        RoomTeam team = getterRoom.getTeam(roomId,teamId)
                .orElseThrow(() -> ApiException.of("ROOM_TEAM_NOT_FOUND",404));
        List<RoomPlayer> players = getterRoom.getPlayers(roomId);
        AiVoiceSeatPolicy.requireEligible(room,teamId,players,user.owner());
        if (players.stream().anyMatch(p -> AiVoiceSeatPolicy.isAi(p.getUid())
                && character.name().equals(p.getName()))) {
            throw ApiException.of("AI_CHARACTER_ALREADY_IN_ROOM",409);
        }
        List<RoomPlayer> members = players.stream().filter(p -> teamId.equals(p.getTeamId())).toList();
        // One scripted bot + an empty second slot: ADD the voice character
        // alongside the scripted partner; do not delete the only teammate.
        // Full test-bot pair (or owner replacing AI's scripted teammate):
        // atomically replace exactly one scripted seat, leaving a valid duo.
        RoomPlayer scripted = members.size() >= 2 ? members.stream()
                .filter(p -> Boolean.TRUE.equals(p.getIsTestBot())
                        && !AiVoiceSeatPolicy.isAi(p.getUid())).findFirst().orElse(null)
                : null;
        if (scripted != null) {
            if (!room.getTestBotIds().contains(scripted.getUid())) {
                throw ApiException.of("AI_REPLACE_ONLY_SCRIPTED_TEST_BOT",409);
            }
            saverRoom.deletePlayer(roomId,scripted.getUid());
            List<String> previous = new ArrayList<>(room.getTestBotIds());
            previous.remove(scripted.getUid());
            room.setTestBotIds(previous);
            Map<String,Object> runtime = new LinkedHashMap<>(room.getTestBotRuntime());
            runtime.remove(scripted.getUid());
            room.setTestBotRuntime(runtime);
        } else if (players.size() >= room.getMaxPlayers()) {
            throw ApiException.of("AI_ROOM_FULL",409);
        }

        List<String> loadout = new ArrayList<>();
        // The game requires five DISTINCT real meme slots per player.
        // Read from the authoritative catalog; never invent fake IDs.
        try {
            for (MemeLibraryPort.PlayableMeme meme : memeLibrary.catalog(120)) {
                if (meme.memeId()!=null && !meme.memeId().isBlank()
                        && meme.videoUrl()!=null && !meme.videoUrl().isBlank()
                        && !loadout.contains(meme.memeId())) {
                    loadout.add(meme.memeId());
                    if (loadout.size()==5) break;
                }
            }
        } catch (RuntimeException ignored) {
            // Fall through to a recoverable 503. No incomplete player is saved.
        }
        if (loadout.size()<5) throw ApiException.of("AI_MEME_CATALOG_NOT_READY",503);

        String aiUid = AiVoiceSeatPolicy.PREFIX + UUID.randomUUID().toString().replace("-","").substring(0,18);
        long now = System.currentTimeMillis();
        RoomPlayer bot = RoomPlayer.builder()
                .roomId(roomId).uid(aiUid).name(character.name())
                .teamId(teamId).isTestBot(true).testBotIndex(null)
                .joinedAt(Instant.now()).lastSeenAt(now)
                .mediaReadyAt(now).mediaRevision(now)
                .cameraEnabled(true).microphoneEnabled(false)
                .memeLoadout(new ArrayList<>(loadout))
                .memeAvailableIds(new ArrayList<>(loadout.subList(0,4)))
                .memeReserveIds(new ArrayList<>(loadout.subList(4,5)))
                .memeRecycleQueue(new ArrayList<>())
                .usedMemeIds(new ArrayList<>())
                .arsenal(new LinkedHashMap<>())
                .build();

        // Rebuild from real player rows, never from stale memberUids invite
        // reservations. Keep valid scripted AI teammates when replacing
        // exactly one seat in full test rooms.
        final String removedScriptedUid=scripted==null?null:scripted.getUid();
        List<String> ids = members.stream()
                // Keep the remaining scripted test player in the actual
                // team roster. Only the chosen replacement UID leaves.
                .filter(p -> !java.util.Objects.equals(p.getUid(),removedScriptedUid))
                .map(RoomPlayer::getUid)
                .filter(uid -> uid != null && !uid.isBlank())
                .distinct()
                .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
        ids.add(aiUid);
        // The site owner can stage the first half of an experimental AI+AI
        // pair; ordinary teams must still contain exactly two players.
        if (ids.size()!=2 && !(user.owner() && ids.size()==1
                && AiVoiceSeatPolicy.isAi(ids.get(0)))) {
            throw ApiException.of("AI_TEAM_SIZE_INVALID",409);
        }
        team.setMemberUids(ids);
        saverRoom.savePlayer(bot);
        saverRoom.saveTeam(team);
        saverRoom.save(room);
        return Map.of("ok",true,"uid",aiUid,"name",bot.getName(),"teamId",teamId,
                "characterId",character.id(),"voiceEnabled",false,"status","waiting_for_agent");
    }

    @Transactional
    public Map<String,Object> remove(HotHatUser user, String roomId, String aiUid) {
        Room room = requireHost(user,roomId);
        if (Boolean.TRUE.equals(room.getRanked())) throw ApiException.of("AI_RANKED_FORBIDDEN",403);
        if (!"setup".equals(room.getPhase())) throw ApiException.of("AI_SETUP_ONLY",409);
        if (!AiVoiceSeatPolicy.isAi(aiUid)) throw ApiException.of("AI_PLAYER_ONLY",400);
        RoomPlayer ai = getterRoom.getPlayer(roomId,aiUid)
                .orElseThrow(()->ApiException.of("AI_PLAYER_NOT_FOUND",404));
        if (!Boolean.TRUE.equals(ai.getIsTestBot())) throw ApiException.of("AI_PLAYER_ONLY",403);
        getterRoom.getTeam(roomId,ai.getTeamId()).ifPresent(team -> {
            List<String> ids = new ArrayList<>(team.getMemberUids());
            ids.remove(aiUid);
            team.setMemberUids(ids);
            saverRoom.saveTeam(team);
        });
        saverRoom.deletePlayer(roomId,aiUid);
        return Map.of("ok",true,"removedUid",aiUid);
    }
}
