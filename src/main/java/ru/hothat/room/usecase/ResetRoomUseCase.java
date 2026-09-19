package ru.hothat.room.usecase;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.hothat.config.HotHatUser;
import ru.hothat.model.room.Room;
import ru.hothat.model.room.RoomPlayer;
import ru.hothat.model.room.RoomTeam;
import ru.hothat.model.room.RoomWordSubmission;
import ru.hothat.repository.GetterRoom;
import ru.hothat.repository.SaverRoom;
import ru.hothat.room.api.dto.ResetRoomRequestDTO;
import ru.hothat.room.api.dto.RoomSetupResponseDTO;

import java.util.ArrayList;
import java.util.List;

/**
 * Вернуть комнату к набору.
 *
 * <p>Сводит две клиентские процедуры в один сценарий и одну транзакцию.
 * {@code backToSetup()} ({@code app-core.js:13508}) обнулял счёт и стирал
 * состояние партии; {@code resetRoom()} ({@code :13570}) вдобавок удалял
 * команды и сданные слова — и делал это тремя записями подряд, с
 * промежуточной фазой {@code resetting}. Оборвись вкладка между второй и
 * третьей, комната оставалась в этой фазе навсегда: ни играть, ни собраться
 * заново в ней было нельзя.
 *
 * <p>Различие между двумя процедурами сохранено флагом, потому что оно
 * продуктовое: «сыграем ещё раз тем же составом» и «собираемся заново» — это
 * разные вечера, и молча делать второе вместо первого значило бы каждый раз
 * рассаживать восьмерых заново.
 *
 * <p>Состояние партии стирается целиком — включая паузу и её причину. Комната,
 * вернувшаяся к набору с застрявшим признаком паузы, показывала бы игрокам
 * заставку «ждём возвращения игрока» поверх экрана настройки.
 */
@Service
@RequiredArgsConstructor
public class ResetRoomUseCase {

    private final RoomAccessGuard roomAuthz;
    private final GetterRoom getterRoom;
    private final SaverRoom saverRoom;
    private final RoomProjections projections;
    private final RoomMatchState roomMatchState;

    @PreAuthorize("hasRole('USER')")
    @Transactional
    public RoomSetupResponseDTO run(HotHatUser user, String roomId, ResetRoomRequestDTO request) {
        Room room = roomAuthz.requireHostRoomForWrite(user, roomId);
        boolean clearTeams = request != null && request.clearTeamsOrDefault();

        List<RoomTeam> teams = getterRoom.getTeams(roomId);
        int clearedSubmissions = 0;
        if (clearTeams) {
            for (RoomTeam team : teams) {
                saverRoom.deleteTeam(roomId, team.getTeamId());
            }
            for (RoomWordSubmission submission : getterRoom.getWordSubmissions(roomId)) {
                saverRoom.deleteWordSubmission(roomId, submission.getSubmissionId());
                clearedSubmissions++;
            }
            teams = List.of();
            room.setTeamOrder(new ArrayList<>());
            room.setWordCount(0);
            // Ревизия слов растёт, даже когда слов не осталось: по ней экраны
            // понимают, что их копия шляпы устарела.
            room.setWordRevision(room.getWordRevision() + 1);
            // Игроки без команд: строка, ссылающаяся на распущенную команду,
            // не даст им сесть заново.
            for (RoomPlayer player : getterRoom.getPlayers(roomId)) {
                if (player.getTeamId() != null) {
                    player.setTeamId(null);
                    saverRoom.savePlayer(player);
                }
            }
        } else {
            for (RoomTeam team : teams) {
                team.setScore(0);
                saverRoom.saveTeam(team);
            }
        }

        roomMatchState.clear(room, teams.stream().map(RoomTeam::getTeamId).toList());
        saverRoom.save(room);
        return new RoomSetupResponseDTO(projections.room(room), projections.teams(teams),
                clearTeams, clearedSubmissions);
    }
}
