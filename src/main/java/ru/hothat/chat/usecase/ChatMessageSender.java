package ru.hothat.chat.usecase;

import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import ru.hothat.chat.api.dto.ChatMessageView;
import ru.hothat.chat.api.dto.ChatRecordingAttachmentView;
import ru.hothat.chat.store.DirectChatStore;
import ru.hothat.config.HotHatUser;
import ru.hothat.chat.spi.DirectChatChangedEvent;
import ru.hothat.util.Ids;

import java.util.List;
import java.util.Map;

/**
 * Общий хвост трёх отправок: текста, фотографии и поделённой записи.
 *
 * <p>Отдельный класс, а не вызов чужого сценария: сценарию запрещено звать
 * сценарий (§7.2), а порядок шагов у всех троих один — завести переписку,
 * записать сообщение, разбудить живой канал, вернуть отправителю созданное.
 * Разъехаться этим шагам нельзя: у трёх адресов один экран.
 *
 * <p>Ответ 201 описывает созданное сообщение, а не отвечает {@code ok:true},
 * как раньше: отправитель рисует его сразу и не ждёт, пока оно приедет живым
 * обновлением. Перечитывать переписку ради этого больше не нужно — строка
 * возвращается той же записью, что её создала.
 */
@Component
@RequiredArgsConstructor
class ChatMessageSender {

    /** Столько символов помещается в сообщение; предел был и у старого движка. */
    private static final int MAX_TEXT = 800;

    private final DirectChatStore chats;
    private final ChatPeerDirectory peerDirectory;
    private final ChatMessageAssembler assembler;
    /** Рассылка живых обновлений: слушателю событие достаётся после коммита. */
    private final ApplicationEventPublisher events;

    ChatMessageView send(HotHatUser user, String peerUid, DirectChatStore.NewMessage draft,
                         Map<String, ChatRecordingAttachmentView> attachments) {
        long chatId = chats.openThread(user.uid(), peerUid);
        DirectChatStore.MessageRow row = chats.append(chatId, user.uid(), peerUid, draft);
        // Публикуем внутри транзакции, но слушатель ждёт коммита: собеседник
        // не должен увидеть сообщение, которого после отката не осталось.
        events.publishEvent(new DirectChatChangedEvent(Ids.pair(user.uid(), peerUid)));
        String nickname = peerDirectory.load(List.of(user.uid())).nickname(user.uid());
        // Приглашений этим путём не отправляют: их кладёт командный порт.
        return assembler.view(row, nickname, attachments, Map.of());
    }

    /**
     * Текст сообщения так, как его хранит база: без сдвоенных пробелов и не
     * длиннее предела. Правило перенесено из старого движка без изменений.
     */
    static String normalized(String text) {
        String clean = (text == null ? "" : text).replaceAll("\\s+", " ").trim();
        return clean.length() > MAX_TEXT ? clean.substring(0, MAX_TEXT) : clean;
    }
}
