package ru.hothat.repository.impl;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import ru.hothat.model.room.Room;

import java.util.List;

@Repository
interface RoomRepo extends JpaRepository<Room, String> {

    List<Room> findByPhase(String phase, Limit limit);

    List<Room> findAllBy(Limit limit);

    List<Room> findByPhaseNot(String phase, Limit limit);
}
