package ru.hothat.friend.usecase;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.hothat.config.HotHatUser;
import ru.hothat.friend.api.dto.IncomingFriendRequestView;
import ru.hothat.friend.api.dto.IncomingFriendRequestsResponseDTO;
import ru.hothat.friend.store.FriendshipStore;

import java.util.ArrayList;
import java.util.List;

/**
 * Показать заявки, ждущие ответа игрока.
 *
 * <p>Читает свои строки и профили отправителей — и ничего больше. Хранилище
 * отдаёт только ждущие ответа (частичный индекс {@code ix_friend_request_inbox}
 * ровно под этот вопрос), поэтому фильтра по статусу здесь нет.
 *
 * <p>Имя отправителя берётся из карточки игрока и только оттуда: копии ника в
 * заявке больше нет. Раньше её писала сама заявка, а правила — смена ника в
 * профиле, то есть у одной колонки было два писателя, и в списке заявок
 * показывалось имя, под которым человек когда-то позвал в друзья.
 */
@Service
@RequiredArgsConstructor
public class ListIncomingFriendRequestsUseCase {

    private final FriendshipStore friendships;
    private final FriendProfileDirectory profileDirectory;

    @PreAuthorize("hasRole('USER')")
    @Transactional(readOnly = true)
    public IncomingFriendRequestsResponseDTO run(HotHatUser user) {
        List<FriendshipStore.RequestRow> requests = friendships.incoming(user.uid(), FriendReadLimits.INCOMING);

        FriendProfileDirectory.Snapshot profiles = profileDirectory.load(
                requests.stream().map(FriendshipStore.RequestRow::requesterUid).toList());

        List<IncomingFriendRequestView> items = new ArrayList<>(requests.size());
        for (FriendshipStore.RequestRow request : requests) {
            String fromUid = request.requesterUid();
            items.add(new IncomingFriendRequestView(
                    request.id(),
                    fromUid,
                    profiles.nickname(fromUid, ""),
                    profiles.avatarDataUrl(fromUid),
                    request.createdAtMs()));
        }
        return new IncomingFriendRequestsResponseDTO(items, null, FriendReadLimits.INCOMING);
    }
}
