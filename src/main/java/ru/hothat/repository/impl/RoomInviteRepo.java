package ru.hothat.repository.impl;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import ru.hothat.model.social.RoomInvite;

@Repository
interface RoomInviteRepo extends JpaRepository<RoomInvite, String> {
}
