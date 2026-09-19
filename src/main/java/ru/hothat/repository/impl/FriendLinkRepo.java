package ru.hothat.repository.impl;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import ru.hothat.model.social.FriendLink;

import java.util.List;

@Repository
interface FriendLinkRepo extends JpaRepository<FriendLink, String> {

    List<FriendLink> findByUidAOrUidB(String uidA, String uidB, Limit limit);
}
