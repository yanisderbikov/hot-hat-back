package ru.hothat.room.usecase;

import org.springframework.stereotype.Component;
import ru.hothat.model.room.Room;
import ru.hothat.model.social.RoomInvite;
import ru.hothat.room.api.dto.RoomInviteState;
import ru.hothat.room.api.dto.RoomInviteStatusView;
import ru.hothat.room.domain.RoomPhase;
import ru.hothat.sabotage.spi.SabotageArmoryPort;

/**
 * Живо ли приглашение в комнату — и что об этом сказать спрашивающему.
 *
 * <p>Одно место на два сценария: построчный список статусов и само принятие.
 * Пока формула жила в одном методе движка, а принятие разбирало её ответ по
 * строковому ключу {@code status}, добавить новую причину отказа значило
 * править обе стороны, и одна из них рано или поздно отстала бы.
 *
 * <p>Приглашение живо, пока комната в наборе и её поколение партии совпадает с
 * зафиксированным при отправке. Второе условие важнее первого: комната, где
 * успели сыграть и вернуться к настройкам, снова в наборе — но зовут в неё уже
 * не на ту игру, и старая ссылка из переписки должна погаснуть.
 *
 * <p>Видимость режется здесь, внутри проекции, а не правом на класс: список
 * принимает до шестидесяти чужих идентификаторов, и предикат к такому запросу
 * неприменим. Чужое приглашение отвечает {@code forbidden} и не называет ни
 * комнаты, ни отправителя: по номеру нельзя узнать, чьё оно.
 */
@Component
public class RoomInvites {

    /** Строку показывают тому, кто зовёт, или тому, кого зовут. */
    public boolean visibleTo(String viewerUid, RoomInvite invite) {
        return viewerUid.equals(invite.getToUid()) || viewerUid.equals(invite.getFromUid());
    }

    /**
     * Состояние приглашения.
     *
     * @param room комната приглашения; {@code null} — её больше нет
     */
    public RoomInviteState stateOf(String viewerUid, RoomInvite invite, Room room, long nowMs) {
        if (!visibleTo(viewerUid, invite)) {
            return RoomInviteState.FORBIDDEN;
        }
        String stored = invite.getStatus() == null ? "pending" : invite.getStatus();
        if (!"pending".equals(stored) && !"accepted".equals(stored)) {
            // Отозвано или отклонено: получателю причина не важна, войти
            // нельзя в обоих случаях.
            return RoomInviteState.UNAVAILABLE;
        }
        Long expiresAtMs = invite.getExpiresAtMs();
        if (expiresAtMs != null && expiresAtMs > 0 && expiresAtMs < nowMs) {
            return RoomInviteState.EXPIRED;
        }
        if (room == null) {
            return RoomInviteState.ROOM_MISSING;
        }
        if (room.isClosed()) {
            return RoomInviteState.ROOM_CLOSED;
        }
        boolean sameGeneration = Math.max(0, room.getGameNumber())
                == Math.max(0, invite.getGameNumberAtInvite());
        if (!RoomPhase.fromWire(room.getPhase()).isSetup() || !sameGeneration) {
            return RoomInviteState.GAME_STARTED;
        }
        return "accepted".equals(stored) ? RoomInviteState.ACCEPTED : RoomInviteState.PENDING;
    }

    /**
     * Строка списка.
     *
     * @param recipientLoadoutCount сколько мемов заряжено у получателя; {@code null},
     *                              если спрашивает отправитель — чужую обойму ему не показывают.
     *                              Порог «сколько нужно» берётся у владельца обоймы, а не из копии:
     *                              обойма одна и та же и на входе в комнату, и на старте
     */
    public RoomInviteStatusView describe(String viewerUid, RoomInvite invite, Room room,
                                         Integer recipientLoadoutCount, long nowMs) {
        RoomInviteState state = stateOf(viewerUid, invite, room, nowMs);
        if (state == RoomInviteState.FORBIDDEN) {
            return new RoomInviteStatusView(invite.getId(), state, false, null, null, null, null);
        }
        String roomName = room != null && room.getName() != null && !room.getName().isBlank()
                ? room.getName()
                : (invite.getRoomName() == null ? "Комната" : invite.getRoomName());
        Boolean loadoutReady = recipientLoadoutCount == null
                ? null : recipientLoadoutCount >= SabotageArmoryPort.LOADOUT_SIZE;
        return new RoomInviteStatusView(invite.getId(), state, state.available(),
                invite.getRoomId(), roomName, loadoutReady, recipientLoadoutCount);
    }

    /** Строка для приглашения, которого нет вовсе. */
    public RoomInviteStatusView missing(String inviteId) {
        return new RoomInviteStatusView(inviteId, RoomInviteState.MISSING, false, null, null, null, null);
    }
}
