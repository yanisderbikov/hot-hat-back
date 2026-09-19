package ru.hothat.conference.store;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/** Дверь в {@code v2.video_conference}; за пределы области не выходит. */
@Repository
interface VideoConferences extends JpaRepository<VideoConference, String> {
}
