package ru.hothat.chat.usecase;

import org.springframework.stereotype.Component;
import ru.hothat.chat.api.dto.ChatImageAttachmentView;
import ru.hothat.chat.api.dto.ChatMessageKind;
import ru.hothat.chat.api.dto.ChatMessageView;
import ru.hothat.chat.api.dto.ChatRecordingAttachmentView;
import ru.hothat.chat.api.dto.ChatRoomInviteView;
import ru.hothat.chat.store.DirectChatStore;
import ru.hothat.util.Json;

import java.util.Map;

/**
 * Строка переписки → проекция сообщения.
 *
 * <p>Собран в одном месте, потому что сообщение показывают четыре ответа
 * (история, три отправки) и кадр живого канала. Раньше это же превращение
 * делал переходный {@code LegacyChatMapper}, разбиравший карты старой службы;
 * теперь разбирать нечего — вид сообщения называет колонка {@code kind},
 * а не догадка по содержимому вложения.
 */
@Component
public class ChatMessageAssembler {

    /** Так старый движок подписывал превью картинки в списке переписок. */
    private static final String IMAGE_PREVIEW = "📷 Фото";

    /** И так — превью поделённой записи. */
    private static final String RECORDING_PREVIEW = "🎬 Запись игры";

    /** А так подписано приглашение, у которого почему-то нет текста. */
    private static final String INVITE_PREVIEW = "🎩 Приглашение в комнату";

    /** Столько символов сообщения помещается в превью списка переписок. */
    private static final int PREVIEW_LENGTH = 200;

    /**
     * Сообщение для ответа.
     *
     * @param row          строка переписки
     * @param fromNickname имя отправителя из карточки игрока; копии имени в
     *                     самом сообщении больше нет — она устаревала при
     *                     первой же смене ника
     * @param recordings   карточки поделённых записей по идентификатору;
     *                     собраны одним запросом на всю страницу
     * @param invites      карточки приглашений в комнату по идентификатору;
     *                     тоже одним запросом, у области комнаты
     */
    public ChatMessageView view(DirectChatStore.MessageRow row, String fromNickname,
                                Map<String, ChatRecordingAttachmentView> recordings,
                                Map<String, ChatRoomInviteView> invites) {
        ChatImageAttachmentView image = row.photo() == null ? null
                : new ChatImageAttachmentView(row.photo().dataUrl(), row.photo().width(),
                        row.photo().height(), blankToNull(row.photo().fileName()));
        ChatRecordingAttachmentView recording = row.recordingId() == null ? null
                : recordings.get(row.recordingId());
        return new ChatMessageView(
                row.id(),
                kind(row.kind()),
                row.fromUid(),
                blankToNull(fromNickname),
                row.toUid(),
                blankToNull(row.text()),
                row.createdAtMs(),
                image,
                recording,
                // Приглашения, которого больше нет, в ответе не будет: пустая
                // карточка нарисовала бы кнопку «войти» в никуда.
                row.roomInviteId() == null ? null : invites.get(row.roomInviteId()));
    }

    /**
     * Начало последнего сообщения для списка переписок.
     *
     * <p>Считается на чтении, а не хранится копией в шапке: копия — это второй
     * писатель у той же строки, и именно она разъезжалась с сообщением, когда
     * шапку переписывал кто-то ещё. Подписи у фото и записи те же, что
     * показывал старый движок.
     */
    public String preview(DirectChatStore.MessageRow row) {
        if (row == null) {
            return null;
        }
        if (row.text() != null && !row.text().isBlank()) {
            return Json.str(row.text(), PREVIEW_LENGTH);
        }
        return switch (row.kind()) {
            case DirectChatStore.IMAGE -> IMAGE_PREVIEW;
            case DirectChatStore.RECORDING -> RECORDING_PREVIEW;
            case DirectChatStore.ROOM_INVITE -> INVITE_PREVIEW;
            default -> null;
        };
    }

    /** Вид из колонки; набор закрыт ограничением базы, но читаем мы его строкой. */
    private static ChatMessageKind kind(String stored) {
        return switch (stored == null ? "" : stored) {
            case DirectChatStore.IMAGE -> ChatMessageKind.IMAGE;
            case DirectChatStore.RECORDING -> ChatMessageKind.RECORDING;
            case DirectChatStore.ROOM_INVITE -> ChatMessageKind.ROOM_INVITE;
            default -> ChatMessageKind.TEXT;
        };
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
