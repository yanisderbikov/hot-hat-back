package ru.hothat.friend.store;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Дверь в {@code v2.friend_request}; за пределы области дружбы не выходит.
 *
 * <p>Оба списка спрашиваются ровно так, как под них сделаны частичные индексы
 * миграции: входящие — только ждущие ответа, исходящие — все, кроме
 * отклонённых (по принятым горит значок в шапке портала).
 */
@Repository
interface FriendshipRequests extends JpaRepository<FriendshipRequest, Long> {

    List<FriendshipRequest> findByAddresseeAndStatusOrderByCreatedAtDesc(UUID addressee, String status, Limit limit);

    List<FriendshipRequest> findByRequesterAndStatusNotOrderByCreatedAtDesc(UUID requester, String status, Limit limit);

    /**
     * Есть ли живая заявка между этими двумя — в любую сторону.
     *
     * <p>Пара считается базой ({@code player_low}/{@code player_high} —
     * вычисляемые колонки), поэтому встречное приглашение находится тем же
     * запросом, что и своё собственное.
     */
    boolean existsByPlayerLowAndPlayerHighAndStatus(UUID playerLow, UUID playerHigh, String status);
}
