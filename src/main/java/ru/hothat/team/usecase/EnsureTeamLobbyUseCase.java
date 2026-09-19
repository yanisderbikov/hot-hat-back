package ru.hothat.team.usecase;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.hothat.config.HotHatUser;
import ru.hothat.model.room.Room;
import ru.hothat.model.room.RoomPlayer;
import ru.hothat.model.room.RoomTeam;
import ru.hothat.repository.GetterRoom;
import ru.hothat.repository.SaverRoom;
import ru.hothat.team.api.dto.TeamLobbyResponseDTO;
import ru.hothat.team.store.TeamStore;
import ru.hothat.util.Ids;

import java.util.ArrayList;

/**
 * Открыть комнату-лобби команды и рассадить в неё пару.
 *
 * <p>Сценарий идемпотентен: идентификатор комнаты выводится из идентификатора
 * команды, поэтому повторный вызов чинит существующую комнату, а не заводит
 * вторую. Именно это и нужно: обоих участников на страницу команды приводит
 * один и тот же адрес, и оба зовут этот сценарий независимо друг от друга.
 *
 * <p>Комната, слот команды и оба места игроков пишутся одной транзакцией. Это
 * та самая транзакция через границу областей, которая в плане идёт через
 * {@code RoomCommandPort}. Комната ещё не переехала на v2, поэтому здесь она
 * пишется своими таблицами напрямую; разрывать границу на события нельзя —
 * пара, у которой создалась комната без мест, увидела бы пустое лобби.
 */
@Service
@RequiredArgsConstructor
public class EnsureTeamLobbyUseCase {

    /** Единственный слот команды в лобби: пара сидит вместе. */
    private static final String LOBBY_TEAM_SLOT = "team-1";

    private final TeamMembershipGuard teamAuthz;
    private final TeamStore teams;
    private final GetterRoom getterRoom;
    private final SaverRoom saverRoom;
    private final TeamProfileDirectory profileDirectory;
    private final TeamCardAssembler cardAssembler;

    @PreAuthorize("hasRole('USER')")
    @Transactional
    public TeamLobbyResponseDTO run(HotHatUser user) {
        TeamStore.TeamRow team = teamAuthz.requireActiveTeam(user);
        TeamProfileDirectory.Snapshot profiles = profileDirectory.load(team.memberUids());

        String roomId = Ids.teamLobbyRoomId(team.teamId());
        long now = System.currentTimeMillis();

        Room room = getterRoom.getById(roomId).orElseGet(() -> Room.builder().id(roomId).build());
        room.setName("Команда " + team.name());
        room.setPhase("setup");
        room.setCreatedBy(team.captainUid());
        room.setTeamLobby(true);
        room.setRankedTeamId(team.teamId());
        room.setIsPrivate(true);
        room.setDivisionLanguage(team.divisionLanguage());
        room.setGameLanguage(team.divisionLanguage());
        room.setClosedAt(null);
        room.setMaxParticipants(2);
        room.setMaxPlayers(2);
        room.setLastActivityAt(now);
        saverRoom.save(room);

        for (String uid : team.memberUids()) {
            RoomPlayer player = getterRoom.getPlayer(roomId, uid)
                    .orElseGet(() -> RoomPlayer.builder().roomId(roomId).uid(uid).build());
            player.setName(profiles.nickname(uid));
            // Комната хранит «нет аватара» пустой строкой; это её формат, а не
            // формат ответа, поэтому null сюда не уходит.
            String avatar = profiles.avatarDataUrl(uid);
            player.setAvatarDataUrl(avatar == null ? "" : avatar);
            player.setTeamId(LOBBY_TEAM_SLOT);
            player.setLastSeenAt(now);
            player.setMediaRevision(now);
            saverRoom.savePlayer(player);
        }

        RoomTeam roomTeam = getterRoom.getTeam(roomId, LOBBY_TEAM_SLOT)
                .orElseGet(() -> RoomTeam.builder().roomId(roomId).teamId(LOBBY_TEAM_SLOT).build());
        roomTeam.setName(team.name());
        roomTeam.setMemberUids(new ArrayList<>(team.memberUids()));
        roomTeam.setRankedTeamId(team.teamId());
        roomTeam.setScore(0);
        saverRoom.saveTeam(roomTeam);

        return new TeamLobbyResponseDTO(
                roomId,
                cardAssembler.card(team, teams.logo(team.teamId()).orElse(null), profiles));
    }
}
