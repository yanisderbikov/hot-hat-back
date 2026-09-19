package ru.hothat.room.usecase;

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
import ru.hothat.room.api.dto.RoomTeamDrawResponseDTO;
import ru.hothat.room.domain.RoomPhase;
import ru.hothat.room.domain.RoomSeatingPolicy;
import ru.hothat.util.Shuffle;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Перемешать составы.
 *
 * <p>Кнопка намеренно не хозяйская ({@code app-core.js:11843}): в комнате
 * незнакомых людей право пересадить всех не должно принадлежать одному.
 *
 * <p>Каждый вызов — полностью новая жеребьёвка, включая уже рассаженных.
 * Половинчатая, которая трогала бы только свободных, выглядела бы как поломка
 * кнопки: нажал «перемешать», а сидишь там же.
 *
 * <p>Команда снимается и с тех, кто в жеребьёвке не участвовал, — с уснувших.
 * Старая серверная жеребьёвка оставляла им прежний {@code teamId}, хотя состав
 * команды переписывала целиком: вернувшийся оказывался в команде, которая его
 * не числит. Здесь состав и строки игроков говорят одно и то же.
 *
 * <p>Случайность живёт здесь, а не в правиле. Домен получает два уже
 * перемешанных списка и раскладывает их детерминированно — тогда рассадку
 * можно проверить, задав перестановку, и не гадать, что выпало генератору.
 * Зрительские места рассаженных снимаются: одно и то же лицо не может быть
 * одновременно за столом и в зале.
 */
@Service
@RequiredArgsConstructor
public class DrawRoomTeamsUseCase {

    private final RoomAccessGuard roomAuthz;
    private final GetterRoom getterRoom;
    private final SaverRoom saverRoom;
    private final RoomSeats roomSeats;
    private final RoomProjections projections;

    @PreAuthorize("hasRole('USER')")
    @Transactional
    public RoomTeamDrawResponseDTO run(HotHatUser user, String roomId) {
        Room room = roomAuthz.requireRoomForWrite(roomId);
        roomAuthz.requireMember(user, roomId);

        long now = System.currentTimeMillis();
        List<RoomTeam> teams = getterRoom.getTeams(roomId);
        List<RoomPlayer> alive = roomSeats.alivePlayers(roomId, now);
        RoomSeatingPolicy.refuseDraw(RoomPhase.fromWire(room.getPhase()), teams.size(), alive.size())
                .ifPresent(refusal -> {
                    throw RoomRefusals.of(refusal);
                });

        List<String> teamIds = teams.stream().map(RoomTeam::getTeamId).toList();
        List<String> slots = new ArrayList<>();
        // По одному кругу на каждое место в команде: так первая пара мест
        // достаётся случайным командам, а не всегда первой по очереди.
        for (int round = 0; round < RoomSeatingPolicy.TEAM_SIZE; round++) {
            slots.addAll(Shuffle.of(teamIds));
        }
        List<String> candidates = Shuffle.of(alive.stream().map(RoomPlayer::getUid).toList());
        RoomSeatingPolicy.Draw draw = RoomSeatingPolicy.draw(candidates, slots);

        int seated = 0;
        for (RoomPlayer player : getterRoom.getPlayers(roomId)) {
            String teamId = teamOf(draw.membersByTeam(), player.getUid());
            player.setTeamId(teamId);
            if (teamId != null) {
                player.setLastSeenAt(now);
                saverRoom.deleteSpectator(roomId, player.getUid());
                seated++;
            }
            saverRoom.savePlayer(player);
        }
        for (RoomTeam team : teams) {
            team.setMemberUids(new ArrayList<>(
                    draw.membersByTeam().getOrDefault(team.getTeamId(), List.of())));
            saverRoom.saveTeam(team);
        }
        room.setTeamOrder(new ArrayList<>(teamIds));
        saverRoom.save(room);

        return new RoomTeamDrawResponseDTO(projections.teams(teams), seated, draw.benched().size());
    }

    private static String teamOf(Map<String, List<String>> membersByTeam, String uid) {
        for (Map.Entry<String, List<String>> entry : membersByTeam.entrySet()) {
            if (entry.getValue().contains(uid)) {
                return entry.getKey();
            }
        }
        return null;
    }
}
