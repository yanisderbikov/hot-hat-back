package ru.hothat.friend.store;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Дверь в {@code v2.friendship}; за пределы области дружбы не выходит.
 *
 * <p>«Друзья игрока» — это поиск по обеим сторонам пары: свой uuid может
 * оказаться и меньшим, и большим. Первая сторона закрыта первичным ключом,
 * вторая — индексом {@code ix_friendship_high}.
 */
@Repository
interface Friendships extends JpaRepository<Friendship, FriendshipId> {

    List<Friendship> findByPlayerLowOrPlayerHighOrderByCreatedAtDesc(UUID low, UUID high, Limit limit);

    long deleteByPlayerLowAndPlayerHigh(UUID low, UUID high);
}
