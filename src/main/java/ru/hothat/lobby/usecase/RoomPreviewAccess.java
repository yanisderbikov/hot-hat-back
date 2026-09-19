package ru.hothat.lobby.usecase;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import ru.hothat.common.api.ErrorCode;
import ru.hothat.lobby.api.dto.LobbyRoomPhase;
import ru.hothat.lobby.domain.LobbyVisibility;
import ru.hothat.model.room.Room;
import ru.hothat.repository.GetterRoom;

/**
 * Какую комнату вообще можно смотреть из лобби.
 *
 * <p>Правило одно на четыре адреса — превью комнаты и три операции над
 * сессией превью, — поэтому и живёт оно в одном месте: иначе «а приватную
 * нельзя» и «а доигранную уже нет» пришлось бы помнить в каждом сценарии, и
 * один из них рано или поздно забыл бы.
 *
 * <p>В плане это предикат из {@code ru.hothat.security.authz}. Пакета
 * безопасности ещё нет, а заводить его из области лобби нельзя, поэтому
 * предусловие стоит здесь — там же, где у переписки стоит
 * {@code ChatPeerGuard}, а у записей {@code RecordingOwnershipGuard}.
 *
 * <p>Все отказы приходят одним кодом {@code ROOM_NOT_FOUND}: для зрителя
 * закрытая, доигранная, приватная и чужая тестовая комнаты неразличимы — во
 * всех четырёх случаях смотреть нечего. Разные коды здесь были бы способом
 * узнать по идентификатору, существует ли приватная комната.
 */
@Component
@RequiredArgsConstructor
public class RoomPreviewAccess {

    private final GetterRoom getterRoom;

    /**
     * Комната, которую этому зрителю можно показать.
     *
     * <p>Отдаёт строку комнаты, а не проекцию: все четыре сценария читают из
     * неё разное, и лишать их этого значило бы либо перечислить здесь весь
     * набор полей на все случаи, либо прочитать комнату вторично.
     */
    public Room requireWatchable(String roomId, String viewerUid) {
        Room room = getterRoom.getById(roomId).orElseThrow(ErrorCode.ROOM_NOT_FOUND::raise);
        boolean live = LobbyRoomPhase.live(room.getPhase()).isPresent();
        LobbyVisibility.RoomFacts facts = new LobbyVisibility.RoomFacts(
                room.getClosedAt() != null,
                Boolean.TRUE.equals(room.getIsPrivate()),
                Boolean.TRUE.equals(room.getManagedMatchmaking()),
                Boolean.TRUE.equals(room.getMatchmakingReady()),
                Boolean.TRUE.equals(room.getIsTestRoom()),
                room.getTestOwnerUid());
        if (!live || !LobbyVisibility.visibleTo(facts, viewerUid)) {
            throw ErrorCode.ROOM_NOT_FOUND.raise();
        }
        return room;
    }
}
