package ru.hothat.conference.store;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Дверь в {@code v2.video_conference_message}; за пределы области не выходит.
 *
 * <p>Окно ленты читается от свежего по индексу
 * {@code ix_video_conference_message_window} и переворачивается уже в памяти.
 */
@Repository
interface VideoConferenceMessages extends JpaRepository<VideoConferenceMessage, Long> {

    List<VideoConferenceMessage> findByConferenceIdOrderByIdDesc(String conferenceId, Limit limit);
}
