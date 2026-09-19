package ru.hothat.chat.usecase;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.hothat.chat.api.dto.ChatThreadView;
import ru.hothat.chat.api.dto.ChatThreadsResponseDTO;
import ru.hothat.chat.store.DirectChatStore;
import ru.hothat.config.HotHatUser;
import ru.hothat.util.Ids;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Сводка личных переписок игрока: с кем, о чём и сколько непрочитанного.
 *
 * <p>Список берётся по своим строкам участия, а не обходом друзей: старый
 * движок читал сотню связей дружбы и на каждой спрашивал шапку переписки и
 * профиль собеседника — до двухсот запросов ради экрана, на котором строк
 * бывает три. Теперь запросов пять, и их число не зависит ни от числа друзей,
 * ни от числа переписок.
 *
 * <p>Транзакция {@code readOnly}: чинить по дороге больше нечего. Прежняя
 * починка потерянных шапок была нужна оттого, что шапка и сообщения жили
 * порознь и могли разойтись; теперь шапку заводит та же транзакция, что
 * пишет первое сообщение.
 *
 * <p>Ник и аватар собеседника берутся из карточки игрока, а не из копии в
 * шапке: копию писал профиль на каждой смене ника, обходя все переписки
 * игрока, — и именно она устаревала.
 */
@Service
@RequiredArgsConstructor
public class ListMyChatThreadsUseCase {

    /**
     * Столько переписок отдаёт адрес за раз. Это же число уезжает в ответ
     * полем {@code limit}: клиенту нужно знать, с чем ходили в базу, чтобы
     * понять, полон ли список.
     */
    private static final int THREADS_LIMIT = 100;

    /** Заглушка имени старого движка: ею подписан автор без имени. */
    private static final String PLACEHOLDER_NICKNAME = "Игрок";

    private final DirectChatStore chats;
    private final ChatPeerDirectory peerDirectory;
    private final ChatMessageAssembler assembler;

    @PreAuthorize("hasRole('USER')")
    @Transactional(readOnly = true)
    public ChatThreadsResponseDTO run(HotHatUser user) {
        String selfUid = user.uid();
        List<DirectChatStore.ThreadRow> threads = chats.threads(selfUid, THREADS_LIMIT);
        if (threads.isEmpty()) {
            return new ChatThreadsResponseDTO(List.of(), null, THREADS_LIMIT, 0);
        }

        List<String> profileUids = new ArrayList<>(threads.size() + 1);
        profileUids.add(selfUid);
        threads.forEach(thread -> profileUids.add(thread.peerUid()));
        ChatPeerDirectory.Snapshot profiles = peerDirectory.load(profileUids);

        List<ChatThreadView> rows = new ArrayList<>(threads.size());
        int totalUnread = 0;
        for (DirectChatStore.ThreadRow thread : threads) {
            DirectChatStore.MessageRow last = thread.lastMessage();
            totalUnread += thread.unreadCount();
            rows.add(new ChatThreadView(
                    // Идентификатор строки прежний — склеенная пара uid:
                    // по нему интерфейс узнаёт переписку и подписывается на неё.
                    Ids.pair(selfUid, thread.peerUid()),
                    thread.peerUid(),
                    profiles.nickname(thread.peerUid()),
                    blankToNull(profiles.avatarDataUrl(thread.peerUid())),
                    assembler.preview(last),
                    last == null ? null : last.fromUid(),
                    last == null ? PLACEHOLDER_NICKNAME : profiles.nickname(last.fromUid()),
                    thread.updatedAtMs(),
                    thread.unreadCount()));
        }

        // Сортировка устойчивая: у переписок с одинаковой отметкой времени
        // порядок остаётся тем, в котором пришли строки участия.
        rows.sort(Comparator.comparingLong(ChatThreadView::updatedAtMs).reversed());
        return new ChatThreadsResponseDTO(rows, null, THREADS_LIMIT, totalUnread);
    }

    /** Пустую строку контракт отдаёт как «поля нет». */
    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
