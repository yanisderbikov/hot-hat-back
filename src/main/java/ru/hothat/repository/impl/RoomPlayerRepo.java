package ru.hothat.repository.impl;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import ru.hothat.model.room.RoomMemberId;
import ru.hothat.model.room.RoomPlayer;

import java.util.List;

@Repository
interface RoomPlayerRepo extends JpaRepository<RoomPlayer, RoomMemberId> {

    /**
     * Места в порядке посадки. Без {@code ORDER BY} Postgres после каждого
     * {@code UPDATE} строки переставляет кортежи, и плитки на экране скакали
     * бы от кадра к кадру; {@code uid} — только чтобы порядок был полным.
     */
    List<RoomPlayer> findByRoomIdOrderByJoinedAtAscUidAsc(String roomId);

    List<RoomPlayer> findByRoomIdIn(List<String> roomIds);

    List<RoomPlayer> findByUid(String uid);

    void deleteByRoomId(String roomId);
}
