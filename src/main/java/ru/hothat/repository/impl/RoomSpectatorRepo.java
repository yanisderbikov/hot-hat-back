package ru.hothat.repository.impl;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import ru.hothat.model.room.RoomMemberId;
import ru.hothat.model.room.RoomSpectator;

import java.util.List;

@Repository
interface RoomSpectatorRepo extends JpaRepository<RoomSpectator, RoomMemberId> {

    /** Зрители в порядке прихода — по той же причине, что и места игроков. */
    List<RoomSpectator> findByRoomIdOrderByJoinedAtAscUidAsc(String roomId);

    List<RoomSpectator> findByUid(String uid);

    void deleteByRoomId(String roomId);
}
