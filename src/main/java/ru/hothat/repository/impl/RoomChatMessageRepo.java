package ru.hothat.repository.impl;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import ru.hothat.model.room.RoomChatMessage;
import ru.hothat.model.room.RoomChatMessageId;

@Repository
interface RoomChatMessageRepo extends JpaRepository<RoomChatMessage, RoomChatMessageId> {

    void deleteByRoomId(String roomId);
}
