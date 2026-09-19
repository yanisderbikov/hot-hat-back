package ru.hothat.repository.impl;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import ru.hothat.model.social.SocialInbox;

@Repository
interface SocialInboxRepo extends JpaRepository<SocialInbox, String> {
}
