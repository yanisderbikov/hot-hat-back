package ru.hothat.room.spi;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import ru.hothat.model.social.RoomInvite;
import ru.hothat.repository.GetterSocial;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Карточки приглашений: реализация {@link RoomInvitePort}.
 *
 * <p>Читает строки прежней таблицы {@code room_invite}: комната ещё не переехала
 * в {@code v2.room}, а у {@code v2.room_invite} на неё внешний ключ — заполнить
 * новую таблицу нельзя, пока пуста та, на которую она ссылается. Когда комната
 * переедет, поменяется этот класс, а спрашивающие — нет.
 *
 * <p>Название комнаты берётся из самого приглашения, а не из комнаты: оно
 * записано на момент отправки и именно в таком виде стоит в тексте сообщения.
 * Комнату могли с тех пор переименовать, и карточка, назвавшая её иначе, чем
 * текст рядом, читалась бы как ошибка.
 */
@Service
@RequiredArgsConstructor
public class RoomInviteDirectory implements RoomInvitePort {

    private final GetterSocial getterSocial;

    @Override
    @Transactional(propagation = Propagation.SUPPORTS, readOnly = true)
    public Map<String, InviteCard> cards(Collection<String> inviteIds) {
        List<String> wanted = new ArrayList<>();
        for (String id : inviteIds) {
            if (id != null && !id.isBlank() && !wanted.contains(id)) {
                wanted.add(id);
            }
        }
        if (wanted.isEmpty()) {
            // Пустой список в базу не идёт: «id in ()» — это запрос ни за чем.
            return Map.of();
        }
        Map<String, InviteCard> byId = new LinkedHashMap<>();
        for (RoomInvite invite : getterSocial.getRoomInvites(wanted)) {
            byId.put(invite.getId(), new InviteCard(
                    invite.getId(),
                    invite.getRoomId(),
                    invite.getRoomName(),
                    invite.getGameNumberAtInvite() == null ? 0 : invite.getGameNumberAtInvite()));
        }
        return byId;
    }
}
