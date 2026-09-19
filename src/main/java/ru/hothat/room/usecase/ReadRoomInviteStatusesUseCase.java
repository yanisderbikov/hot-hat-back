package ru.hothat.room.usecase;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.hothat.config.HotHatUser;
import ru.hothat.model.room.Room;
import ru.hothat.model.social.RoomInvite;
import ru.hothat.repository.GetterSocial;
import ru.hothat.room.api.dto.RoomInviteStatusQueryDTO;
import ru.hothat.room.api.dto.RoomInviteStatusView;
import ru.hothat.room.api.dto.RoomInviteStatusesResponseDTO;
import ru.hothat.room.store.RoomRows;
import ru.hothat.sabotage.spi.SabotageArmoryPort;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Показать состояние выданных приглашений.
 *
 * <p>Страница переписки держит на экране до шестидесяти карточек и опрашивает
 * их разом ({@code realtime-social.js:71}) — отсюда и список на входе.
 *
 * <p>Три чтения на весь ответ независимо от длины списка: приглашения одной
 * выборкой, их комнаты второй, своя карточка третьей. Старый движок читал
 * комнату каждого приглашения по отдельности, сглаживая это картой в памяти
 * метода, — до шестидесяти обращений на открытие чата (находка B8 про веер
 * чтений).
 *
 * <p>Право здесь общее — любой вошедший, — а видимость режется построчно в
 * проекции: предикат уровня класса к списку из шестидесяти чужих
 * идентификаторов неприменим в принципе.
 */
@Service
@RequiredArgsConstructor
public class ReadRoomInviteStatusesUseCase {

    private final GetterSocial getterSocial;
    private final RoomRows roomRows;
    /** Обойма мемов принадлежит области диверсий, а не оболочке учётки. */
    private final SabotageArmoryPort armory;
    private final RoomInvites roomInvites;

    @PreAuthorize("hasRole('USER')")
    @Transactional(readOnly = true)
    public RoomInviteStatusesResponseDTO run(HotHatUser user, RoomInviteStatusQueryDTO query) {
        List<String> ids = normalize(query.inviteIds());
        if (ids.isEmpty()) {
            return new RoomInviteStatusesResponseDTO(List.of(), null, 0);
        }

        Map<String, RoomInvite> invitesById = new HashMap<>();
        for (RoomInvite invite : getterSocial.getRoomInvites(ids)) {
            invitesById.put(invite.getId(), invite);
        }
        Map<String, Room> roomsById = loadRooms(user, invitesById.values());
        Integer myLoadoutCount = null;
        boolean addressedToMe = invitesById.values().stream()
                .anyMatch(invite -> user.uid().equals(invite.getToUid()));
        if (addressedToMe) {
            // Читающий сценарий ничего не заводит: транзакция объявлена
            // readOnly, и запись в ней тихо не доехала бы до базы. Обоймы может
            // не быть вовсе у совсем новой учётки — ноль тогда верный ответ.
            myLoadoutCount = armory.loadout(user.uid()).size();
        }

        long now = System.currentTimeMillis();
        List<RoomInviteStatusView> items = new ArrayList<>(ids.size());
        for (String id : ids) {
            RoomInvite invite = invitesById.get(id);
            if (invite == null) {
                items.add(roomInvites.missing(id));
                continue;
            }
            Integer loadoutCount = user.uid().equals(invite.getToUid()) ? myLoadoutCount : null;
            items.add(roomInvites.describe(user.uid(), invite, roomsById.get(invite.getRoomId()),
                    loadoutCount, now));
        }
        return new RoomInviteStatusesResponseDTO(items, null, ids.size());
    }

    /**
     * Комнаты приглашений — одной выборкой.
     *
     * <p>Комнаты чужих приглашений не читаются вовсе: их состояние всё равно не
     * попадёт в ответ, а лишнее чтение выдало бы существование комнаты тому,
     * кому приглашение не адресовано.
     */
    private Map<String, Room> loadRooms(HotHatUser user, Iterable<RoomInvite> invites) {
        List<String> roomIds = new ArrayList<>();
        for (RoomInvite invite : invites) {
            if (roomInvites.visibleTo(user.uid(), invite) && !roomIds.contains(invite.getRoomId())) {
                roomIds.add(invite.getRoomId());
            }
        }
        Map<String, Room> byId = new LinkedHashMap<>();
        if (!roomIds.isEmpty()) {
            for (Room room : roomRows.findAll(roomIds)) {
                byId.put(room.getId(), room);
            }
        }
        return byId;
    }

    /** Повторы и пустые строки отбрасываются; порядок спрашивающего сохраняется. */
    private static List<String> normalize(List<String> raw) {
        List<String> ids = new ArrayList<>();
        for (String value : raw == null ? List.<String>of() : raw) {
            String id = value == null ? "" : value.trim();
            if (!id.isEmpty() && !ids.contains(id)) {
                ids.add(id);
            }
            if (ids.size() >= RoomInviteStatusQueryDTO.MAX_IDS) {
                break;
            }
        }
        return ids;
    }
}
