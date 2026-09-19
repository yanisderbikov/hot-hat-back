package ru.hothat.room.usecase;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.hothat.chat.spi.ChatCommandPort;
import ru.hothat.config.ApiException;
import ru.hothat.config.HotHatUser;
import ru.hothat.model.room.Room;
import ru.hothat.model.social.RoomInvite;
import ru.hothat.profile.spi.PlayerCardPort;
import ru.hothat.repository.GetterRoom;
import ru.hothat.repository.SaverSocial;
import ru.hothat.room.api.dto.SendRoomInviteRequestDTO;
import ru.hothat.room.api.dto.SentRoomInviteResponseDTO;
import ru.hothat.room.domain.RoomPhase;
import ru.hothat.util.Ids;
import ru.hothat.util.Json;

import java.time.Instant;

/**
 * Позвать друга в свою комнату.
 *
 * <p>Это одна из девяти транзакций через границу областей (§7.3): приглашение,
 * сообщение переписки и счётчик непрочитанного пишутся вместе. Разорвать их на
 * событие нельзя — карточка в чате без приглашения это кнопка, ведущая в
 * отказ, а приглашение без карточки не увидит никто.
 *
 * <p>Границу держит {@link ChatCommandPort}: строку переписки, сообщение и
 * значок входящих пишет их владелец, а комната лишь просит. У него же
 * спрашивается «кому можно писать» — формула одна на все адреса переписки, и
 * вторая её копия здесь разошлась бы с первой на первой же правке.
 *
 * <p>Что принадлежит комнате и осталось здесь: хозяйство и фаза набора,
 * запрет звать в рейтинговую комнату, признак «он уже внутри» и срок жизни
 * приглашения — сутки.
 *
 * <p>«Уже внутри» — обычное поле ответа, а не вторая его форма: прежний движок
 * отвечал в этом случае картой из двух ключей, и клиент различал ветки по
 * отсутствию {@code inviteId} (замечание C6).
 */
@Service
@RequiredArgsConstructor
public class SendRoomInviteUseCase {

    /** Приглашение живёт сутки — тот же срок, что записывал прежний движок. */
    private static final long INVITE_TTL_MS = 24 * 60 * 60 * 1000L;

    /** Столько знаков названия комнаты помещается в карточку приглашения. */
    private static final int MAX_ROOM_NAME = 80;

    private final RoomAccessGuard roomAuthz;
    private final GetterRoom getterRoom;
    private final SaverSocial saverSocial;
    /** Имя зовущего — из карточки игрока, а не из тела запроса. */
    private final PlayerCardPort cards;
    /** Переписку пишет её область; комната только просит. */
    private final ChatCommandPort chat;

    @PreAuthorize("hasRole('USER')")
    @Transactional
    public SentRoomInviteResponseDTO run(HotHatUser user, String roomId,
                                         SendRoomInviteRequestDTO request) {
        Room room = roomAuthz.requireRoomForWrite(roomId);
        // Код отказа здесь свой, а не общий HOST_ONLY: спецификация этого
        // адреса называет ROOM_HOST_ONLY, и его читает экран переписки.
        if (!user.uid().equals(room.getCreatedBy())) {
            throw ApiException.of("ROOM_HOST_ONLY", 403);
        }
        if (room.isClosed()) {
            throw ApiException.of("ROOM_CLOSED", 409);
        }
        if (!RoomPhase.fromWire(room.getPhase()).isSetup()) {
            throw ApiException.of("ROOM_SETUP_ONLY", 409);
        }
        if (Boolean.TRUE.equals(room.getRanked())) {
            // В рейтинговую комнату состав приводит подбор, а не приглашение:
            // позвать туда друга значило бы посадить его мимо пары.
            throw ApiException.of("ROOM_INVITE_UNAVAILABLE", 409);
        }
        String friendUid = chat.requireWritablePeer(user.uid(), request.friendUid());

        String roomName = Json.str(RoomProjections.displayName(room), MAX_ROOM_NAME);
        if (getterRoom.getPlayer(room.getId(), friendUid).isPresent()
                || getterRoom.getSpectator(room.getId(), friendUid).isPresent()) {
            // Он уже здесь: второе приглашение ничего не добавит, а карточка в
            // переписке позвала бы туда, где человек и так сидит.
            return new SentRoomInviteResponseDTO(null, room.getId(), roomName, true, null);
        }

        long now = System.currentTimeMillis();
        long expiresAtMs = now + INVITE_TTL_MS;
        String inviteId = "ri-" + Ids.hex(10);
        String hostNickname = cards.card(user.uid()).map(PlayerCardPort.Card::nickname).orElse(null);
        String text = "Приглашение в комнату «" + roomName + "»";
        int gameNumberAtInvite = Math.max(0, room.getGameNumber() == null ? 0 : room.getGameNumber());

        RoomInvite invite = saverSocial.saveRoomInvite(RoomInvite.builder()
                .id(inviteId)
                .roomId(room.getId())
                .roomName(roomName)
                .fromUid(user.uid())
                .fromNickname(hostNickname)
                .toUid(friendUid)
                .status("pending")
                .gameNumberAtInvite(gameNumberAtInvite)
                .chatId(Ids.pair(user.uid(), friendUid))
                .createdAt(Instant.now())
                .createdAtMs(now)
                .expiresAtMs(expiresAtMs)
                .build());

        long messageId = chat.appendRoomInvite(new ChatCommandPort.RoomInviteCard(
                user.uid(), friendUid, inviteId, room.getId(), roomName, hostNickname, text));
        // Обратная ссылка на сообщение дописывается вторым шагом: номер выдаёт
        // база, и до записи его не существует. Строка та же самая — она уже в
        // контексте хранилища, второго чтения здесь не происходит.
        invite.setMessageId(String.valueOf(messageId));
        saverSocial.saveRoomInvite(invite);

        return new SentRoomInviteResponseDTO(inviteId, room.getId(), roomName, false, expiresAtMs);
    }
}
