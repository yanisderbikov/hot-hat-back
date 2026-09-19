package ru.hothat.friend.usecase;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.hothat.config.HotHatUser;
import ru.hothat.friend.api.dto.FriendRequestStatus;
import ru.hothat.friend.api.dto.OutgoingFriendRequestView;
import ru.hothat.friend.api.dto.OutgoingFriendRequestsResponseDTO;
import ru.hothat.friend.store.FriendshipStore;

import java.util.ArrayList;
import java.util.List;

/**
 * Показать заявки, отправленные игроком.
 *
 * <p>Порядок сохранён прежний: сначала ждущие ответа — их показывает экран
 * заявок, — затем принятые, которыми живёт значок в шапке. Отклонённые не
 * приезжают из базы вовсе: под этот вопрос сделан частичный индекс
 * {@code ix_friend_request_outbox} с предикатом «не отклонена».
 */
@Service
@RequiredArgsConstructor
public class ListOutgoingFriendRequestsUseCase {

    private final FriendshipStore friendships;
    private final FriendProfileDirectory profileDirectory;

    @PreAuthorize("hasRole('USER')")
    @Transactional(readOnly = true)
    public OutgoingFriendRequestsResponseDTO run(HotHatUser user) {
        List<FriendshipStore.RequestRow> requests = friendships.outgoing(user.uid(), FriendReadLimits.OUTGOING);

        // Профили нужны только ждущим ответа: у принятой заявки ответ не несёт
        // ни ника, ни аватара, и читать их значило бы платить за пустое поле.
        FriendProfileDirectory.Snapshot profiles = profileDirectory.load(requests.stream()
                .filter(request -> FriendshipStore.PENDING.equals(request.status()))
                .map(FriendshipStore.RequestRow::addresseeUid)
                .toList());

        List<OutgoingFriendRequestView> items = new ArrayList<>(requests.size());
        for (FriendshipStore.RequestRow request : requests) {
            if (!FriendshipStore.PENDING.equals(request.status())) {
                continue;
            }
            String toUid = request.addresseeUid();
            items.add(new OutgoingFriendRequestView(
                    request.id(),
                    toUid,
                    FriendRequestStatus.PENDING,
                    blankToNull(profiles.nickname(toUid, "")),
                    profiles.avatarDataUrl(toUid),
                    request.createdAtMs(),
                    null));
        }
        for (FriendshipStore.RequestRow request : requests) {
            if (!FriendshipStore.ACCEPTED.equals(request.status())) {
                continue;
            }
            items.add(new OutgoingFriendRequestView(
                    request.id(),
                    request.addresseeUid(),
                    FriendRequestStatus.ACCEPTED,
                    null,
                    null,
                    0L,
                    request.answeredAtMs()));
        }
        return new OutgoingFriendRequestsResponseDTO(items, null, FriendReadLimits.OUTGOING);
    }

    /** Пустой ник в контракте выражен null: у поля объявлено nullable. */
    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
