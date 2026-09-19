package ru.hothat.chat.store;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Component;
import ru.hothat.common.identity.LegacyIdBridge;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Единственная дверь области переписки в свои таблицы.
 *
 * <p>Наружу отдаёт маленькие записи, а не сущности: четыре класса
 * {@code chat.store} не публичны и в сигнатуры сценариев не попадают. Здесь же
 * живёт перевод uid ↔ uuid, поэтому сценарии продолжают говорить теми
 * идентификаторами, которые стоят в ответах.
 *
 * <p>Ни одно чтение не ходит в базу в цикле. Список переписок — это четыре
 * запроса на любое число собеседников: свои строки участия, шапки, последние
 * сообщения и обратный перевод игроков. Страница истории — три: сообщения,
 * их картинки и обратный перевод отправителей.
 */
@Component
@RequiredArgsConstructor
public class DirectChatStore {

    /** Виды сообщения; набор закрыт ограничением базы. */
    public static final String TEXT = "text";
    public static final String IMAGE = "image";
    public static final String RECORDING = "recording";
    /** Приглашение в комнату: карточка со ссылкой на комнату и кнопкой «войти». */
    public static final String ROOM_INVITE = "room_invite";

    private final DirectChatThreads threads;
    private final DirectChatMemberships memberships;
    private final DirectMessages messages;
    private final DirectMessagePhotos photos;
    private final LegacyIdBridge ids;

    /** Переписка этой пары, если она уже заведена. */
    public Optional<Long> findThread(String uidA, String uidB) {
        UUID a = ids.playerId(uidA);
        UUID b = ids.playerId(uidB);
        return threads.findByPlayerLowAndPlayerHigh(LegacyIdBridge.low(a, b), LegacyIdBridge.high(a, b))
                .map(DirectChatThread::getChatId);
    }

    /**
     * Переписка пары; заводится, если её ещё нет.
     *
     * <p>Зовётся только с записи: чтение истории пустой переписки шапку больше
     * не создаёт. Старый движок создавал — и в списке переписок появлялась
     * строка без единого сообщения только оттого, что диалог однажды открыли.
     */
    public long openThread(String selfUid, String peerUid) {
        ids.rememberPlayers(List.of(selfUid, peerUid));
        UUID self = ids.playerId(selfUid);
        UUID peer = ids.playerId(peerUid);
        UUID low = LegacyIdBridge.low(self, peer);
        UUID high = LegacyIdBridge.high(self, peer);

        threads.insertIfAbsent(low, high);
        long chatId = threads.findByPlayerLowAndPlayerHigh(low, high)
                .map(DirectChatThread::getChatId)
                .orElseThrow(() -> new IllegalStateException("Переписка не завелась: " + low + "/" + high));
        memberships.insertIfAbsent(chatId, self);
        memberships.insertIfAbsent(chatId, peer);
        return chatId;
    }

    /**
     * Последние сообщения переписки, от старых к новым — в порядке показа.
     *
     * <p>Собеседники передаются сюда, а не вычитываются из шапки: их уже знает
     * вызывающий, и второй поход за парой ничего не добавил бы.
     */
    public List<MessageRow> history(long chatId, String selfUid, String peerUid, int limit) {
        List<DirectMessage> found = new ArrayList<>(messages.findByChatIdOrderByIdDesc(chatId, Limit.of(limit)));
        Collections.reverse(found);
        return rows(found, selfUid, peerUid, withPhotos(found));
    }

    /**
     * Записать сообщение.
     *
     * <p>Порядок шагов важен: сначала строка сообщения (её идентификатор нужен
     * шапке), потом картинка, потом указатель на последнее сообщение и только
     * затем счётчик непрочитанного собеседника. Всё это — одна транзакция
     * сценария: сообщение без счётчика означало бы письмо, о котором не
     * загорится значок.
     */
    public MessageRow append(long chatId, String fromUid, String toUid, NewMessage draft) {
        UUID sender = ids.playerId(fromUid);
        DirectMessage saved = messages.saveAndFlush(DirectMessage.builder()
                .chatId(chatId)
                .senderPlayerId(sender)
                .kind(draft.kind())
                .body(draft.body())
                .sharedRecordingId(draft.recordingId() == null ? null : ids.recordingId(draft.recordingId()))
                .roomInviteId(draft.roomInviteId() == null ? null : ids.roomInviteId(draft.roomInviteId()))
                .createdAt(Instant.now())
                .build());
        if (draft.recordingId() != null) {
            // Обратный перевод записи понадобится читателю истории: в колонке
            // лежит uuid, а карточку в переписке рисует старый идентификатор.
            ids.rememberRecording(draft.recordingId());
        }
        if (draft.roomInviteId() != null) {
            // То же и у приглашения: без строки моста кнопка «войти» осталась
            // бы без комнаты.
            ids.rememberRoomInvite(draft.roomInviteId());
        }
        PhotoRow photo = draft.photo();
        if (photo != null) {
            photos.save(DirectMessagePhoto.builder()
                    .messageId(saved.getId())
                    .dataUrl(photo.dataUrl())
                    .width(photo.width())
                    .height(photo.height())
                    .fileName(photo.fileName())
                    .build());
        }
        threads.pointAtLastMessage(chatId, saved.getId(), saved.getCreatedAt());
        memberships.bumpUnread(chatId, ids.playerId(toUid), Instant.now());

        return new MessageRow(saved.getId(), saved.getKind(), fromUid, toUid, saved.getBody(),
                saved.getCreatedAt().toEpochMilli(), photo, draft.recordingId(), draft.roomInviteId());
    }

    /**
     * Переписки игрока — по своим строкам участия, а не обходом списка друзей.
     *
     * <p>Предел применяется к перепискам: старый движок брал сотню связей
     * дружбы и на каждой спрашивал шапку, поэтому у игрока с сотней друзей и
     * тремя диалогами вопросов было сто, а строк — три.
     */
    public List<ThreadRow> threads(String selfUid, int limit) {
        UUID self = ids.playerId(selfUid);
        List<DirectChatMembership> mine = memberships.findByPlayerIdOrderByChatIdDesc(self, Limit.of(limit));
        if (mine.isEmpty()) {
            return List.of();
        }

        List<Long> chatIds = mine.stream().map(DirectChatMembership::getChatId).toList();
        Map<Long, DirectChatThread> headers = new HashMap<>();
        for (DirectChatThread thread : threads.findAllById(chatIds)) {
            headers.put(thread.getChatId(), thread);
        }

        List<Long> lastMessageIds = headers.values().stream()
                .map(DirectChatThread::getLastMessageId)
                .filter(Objects::nonNull)
                .toList();
        Map<Long, DirectMessage> lastMessages = new HashMap<>();
        for (DirectMessage message : messages.findAllById(lastMessageIds)) {
            lastMessages.put(message.getId(), message);
        }

        // Обратный перевод — один на весь список: и собеседники, и авторы
        // последних сообщений спрашиваются вместе.
        List<UUID> players = new ArrayList<>();
        for (DirectChatThread header : headers.values()) {
            players.add(header.getPlayerLow());
            players.add(header.getPlayerHigh());
        }
        Map<UUID, String> uids = ids.playerUids(players);

        List<ThreadRow> rows = new ArrayList<>(mine.size());
        for (DirectChatMembership membership : mine) {
            DirectChatThread header = headers.get(membership.getChatId());
            if (header == null) {
                continue;
            }
            UUID peer = self.equals(header.getPlayerLow()) ? header.getPlayerHigh() : header.getPlayerLow();
            String peerUid = uids.get(peer);
            if (peerUid == null || peerUid.isBlank()) {
                // Собеседник не переводится обратно — строка списка без
                // человека; показывать её нечем.
                continue;
            }
            DirectMessage last = header.getLastMessageId() == null ? null
                    : lastMessages.get(header.getLastMessageId());
            MessageRow lastRow = last == null ? null
                    : row(last, uids.getOrDefault(last.getSenderPlayerId(), ""), selfUid, peerUid,
                            null, null, null);
            long updatedAtMs = header.getLastMessageAt() != null
                    ? header.getLastMessageAt().toEpochMilli()
                    : (header.getCreatedAt() == null ? 0L : header.getCreatedAt().toEpochMilli());
            rows.add(new ThreadRow(header.getChatId(), peerUid,
                    Math.max(0, membership.getUnreadCount()), lastRow, updatedAtMs));
        }
        return rows;
    }

    /** Сумма непрочитанного по всем перепискам — одно число из базы. */
    public int totalUnread(String selfUid) {
        return (int) Math.max(0, memberships.totalUnread(ids.playerId(selfUid)));
    }

    /** Последнее письмо, пришедшее игроку; пусто — писем не было. */
    public Optional<MessageRow> lastIncoming(String selfUid) {
        UUID self = ids.playerId(selfUid);
        List<DirectMessage> found = messages.findLastIncoming(self, Limit.of(1));
        if (found.isEmpty()) {
            return Optional.empty();
        }
        DirectMessage message = found.get(0);
        String fromUid = ids.playerUids(List.of(message.getSenderPlayerId())).get(message.getSenderPlayerId());
        if (fromUid == null || fromUid.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(row(message, fromUid, selfUid, fromUid, null, null, null));
    }

    /**
     * Отметить переписку прочитанной; возвращает, сколько непрочитанного было.
     *
     * <p>Водяной знак ставится на последнее сообщение шапки: «дочитано досюда»
     * переживает удаление самого сообщения, поэтому внешнего ключа у него нет.
     */
    public int markRead(long chatId, String selfUid) {
        UUID self = ids.playerId(selfUid);
        DirectChatMembership membership = memberships
                .findById(new DirectChatMembershipId(chatId, self))
                .orElse(null);
        if (membership == null) {
            return 0;
        }
        int cleared = Math.max(0, membership.getUnreadCount());
        membership.setUnreadCount(0);
        threads.findById(chatId).map(DirectChatThread::getLastMessageId)
                .ifPresent(membership::setLastReadMessageId);
        membership.setUpdatedAt(Instant.now());
        memberships.save(membership);
        return cleared;
    }

    /**
     * Забыть участие пары в переписке: эти двое больше не друзья.
     *
     * <p>Написанное остаётся на месте — уходят только строки участия, а с ними
     * и переписка из списка, и её непрочитанное из значка. Иначе бывший друг
     * оставался бы в списке с непогасимым счётчиком: открыть переписку уже
     * нельзя, а значит нельзя и отметить её прочитанной.
     */
    public void forgetPair(String uidA, String uidB) {
        findThread(uidA, uidB).ifPresent(memberships::deleteMembers);
    }

    /** Картинка сообщения так, как её видит сценарий. */
    public record PhotoRow(String dataUrl, int width, int height, String fileName) {
    }

    /**
     * Сообщение так, как его видит сценарий. Получатель здесь есть, хотя в
     * таблице его нет: в переписке двое, и второй — тот из пары, кто не
     * отправитель; вычислять это в каждом сценарии заново незачем.
     */
    public record MessageRow(long id, String kind, String fromUid, String toUid, String text,
                             long createdAtMs, PhotoRow photo, String recordingId, String roomInviteId) {
    }

    /** Строка списка переписок; последнего сообщения может не быть. */
    public record ThreadRow(long chatId, String peerUid, int unreadCount, MessageRow lastMessage,
                            long updatedAtMs) {
    }

    /**
     * Что записать: вид, текст и ровно одно наполнение — картинка, запись или
     * приглашение в комнату. Согласие вида с наполнением проверяет база: у
     * записи заполнена запись, у приглашения — приглашение.
     */
    public record NewMessage(String kind, String body, String recordingId, PhotoRow photo,
                             String roomInviteId) {

        /** Сообщение без ссылок на чужие области: текст и картинка. */
        public static NewMessage plain(String kind, String body, PhotoRow photo) {
            return new NewMessage(kind, body, null, photo, null);
        }

        /** Поделённая запись игры. */
        public static NewMessage recording(String body, String recordingId) {
            return new NewMessage(RECORDING, body, recordingId, null, null);
        }

        /** Приглашение в комнату: текст показывается превью в списке переписок. */
        public static NewMessage roomInvite(String body, String inviteId) {
            return new NewMessage(ROOM_INVITE, body, null, null, inviteId);
        }
    }

    /** Картинки страницы — одним запросом и только для сообщений-картинок. */
    private Map<Long, PhotoRow> withPhotos(List<DirectMessage> found) {
        List<Long> imageIds = found.stream()
                .filter(message -> IMAGE.equals(message.getKind()))
                .map(DirectMessage::getId)
                .toList();
        if (imageIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, PhotoRow> byMessage = new HashMap<>();
        for (DirectMessagePhoto photo : photos.findByMessageIdIn(imageIds)) {
            byMessage.put(photo.getMessageId(), new PhotoRow(
                    photo.getDataUrl(), photo.getWidth(), photo.getHeight(), photo.getFileName()));
        }
        return byMessage;
    }

    private List<MessageRow> rows(List<DirectMessage> found, String selfUid, String peerUid,
                                  Map<Long, PhotoRow> photosByMessage) {
        List<UUID> senders = found.stream().map(DirectMessage::getSenderPlayerId).toList();
        Map<UUID, String> uids = ids.playerUids(senders);
        Map<UUID, String> recordings = ids.recordingIds(found.stream()
                .map(DirectMessage::getSharedRecordingId)
                .filter(Objects::nonNull)
                .toList());
        // Приглашения страницы переводятся обратно тем же одним запросом, что
        // и записи: карточек в диалоге бывает много, и спрашивать их поодиночке
        // значило бы вернуть тот самый веер чтений, ради которого всё затевалось.
        Map<UUID, String> invites = ids.roomInviteIds(found.stream()
                .map(DirectMessage::getRoomInviteId)
                .filter(Objects::nonNull)
                .toList());

        List<MessageRow> result = new ArrayList<>(found.size());
        for (DirectMessage message : found) {
            result.add(row(message, uids.getOrDefault(message.getSenderPlayerId(), ""), selfUid, peerUid,
                    photosByMessage.get(message.getId()),
                    message.getSharedRecordingId() == null ? null
                            : recordings.get(message.getSharedRecordingId()),
                    message.getRoomInviteId() == null ? null
                            : invites.get(message.getRoomInviteId())));
        }
        return result;
    }

    private static MessageRow row(DirectMessage message, String fromUid, String selfUid, String peerUid,
                                  PhotoRow photo, String recordingId, String roomInviteId) {
        String toUid = fromUid.equals(selfUid) ? peerUid : selfUid;
        return new MessageRow(message.getId(), message.getKind(), fromUid, toUid, message.getBody(),
                message.getCreatedAt() == null ? 0L : message.getCreatedAt().toEpochMilli(),
                photo, recordingId, roomInviteId);
    }
}
