package ru.hothat.chat.usecase;

import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import ru.hothat.chat.store.DirectChatStore;
import ru.hothat.friend.spi.FriendshipEvents;

/**
 * Что переписка делает, когда двое перестали быть друзьями.
 *
 * <p>Список переписок собирается по своим строкам участия, а не обходом
 * друзей — именно этим он и стоит пять запросов вместо двухсот. Плата за
 * развязку одна: об исчезнувшей дружбе список сам не узнает, и без этого
 * слушателя бывший друг оставался бы в нём вместе с непрочитанным, которое
 * невозможно погасить ({@link ChatPeerGuard} закрывает и чтение, и отметку
 * прочтения). Прежняя служба обходила связи дружбы и такого не показывала.
 *
 * <p>Слушатель обычный, а не {@code AFTER_COMMIT}: уборка обязана попасть в
 * ту же транзакцию, что и разрыв. Отдельная транзакция после коммита могла бы
 * не состояться и оставить ровно ту рассинхронизацию, ради которой заведена.
 */
@Component
@RequiredArgsConstructor
public class ChatFriendshipListener {

    private final DirectChatStore chats;

    @EventListener
    public void onUnlinked(FriendshipEvents.Unlinked event) {
        chats.forgetPair(event.uidA(), event.uidB());
    }
}
