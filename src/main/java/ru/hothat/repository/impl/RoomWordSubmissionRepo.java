package ru.hothat.repository.impl;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import ru.hothat.model.room.RoomWordSubmission;
import ru.hothat.model.room.RoomWordSubmissionId;

import java.util.List;

@Repository
interface RoomWordSubmissionRepo extends JpaRepository<RoomWordSubmission, RoomWordSubmissionId> {

    List<RoomWordSubmission> findByRoomId(String roomId);

    void deleteByRoomId(String roomId);
}
