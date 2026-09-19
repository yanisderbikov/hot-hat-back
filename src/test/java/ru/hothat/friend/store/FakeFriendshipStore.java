package ru.hothat.friend.store;

import java.util.Optional;

/**
 * Хранилище дружбы с одной заявкой: правило прав спрашивает только её.
 *
 * <p>В пакете хранилища по той же причине, что и у команды: репозитории в
 * конструкторе не видны снаружи.
 */
public final class FakeFriendshipStore extends FriendshipStore {

    private FriendshipStore.RequestRow request;

    private FakeFriendshipStore() {
        super(null, null, null);
    }

    /** Заявок нет вовсе: несуществующий номер отвечает так же, как чужой. */
    public static FakeFriendshipStore empty() {
        return new FakeFriendshipStore();
    }

    public static FakeFriendshipStore withRequest(long id, String requesterUid, String addresseeUid) {
        FakeFriendshipStore store = new FakeFriendshipStore();
        store.request = new FriendshipStore.RequestRow(
                id, requesterUid, addresseeUid, FriendshipStore.PENDING, 0L, null);
        return store;
    }

    @Override
    public Optional<RequestRow> request(long requestId) {
        return Optional.ofNullable(request).filter(row -> row.id() == requestId);
    }
}
