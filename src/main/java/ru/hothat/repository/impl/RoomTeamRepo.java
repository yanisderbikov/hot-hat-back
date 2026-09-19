package ru.hothat.repository.impl;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import ru.hothat.model.room.RoomTeam;
import ru.hothat.model.room.RoomTeamId;

import java.util.List;

@Repository
interface RoomTeamRepo extends JpaRepository<RoomTeam, RoomTeamId> {

    @Query("select t from RoomTeam t where t.roomId = :roomId order by t.order asc, t.teamId asc")
    List<RoomTeam> findByRoomIdOrderByTeamOrderAscTeamIdAsc(@Param("roomId") String roomId);

    void deleteByRoomId(String roomId);
}
