package ru.hothat.conference.store;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Дверь в {@code v2.video_conference_member}; за пределы области не выходит.
 *
 * <p>Состав видео-чата читается целиком одной выборкой, а свои ждущие
 * приглашения — по частичному индексу {@code ix_video_conference_member_pending}.
 */
@Repository
interface VideoConferenceMembers extends JpaRepository<VideoConferenceMember, VideoConferenceMemberId> {

    List<VideoConferenceMember> findByConferenceIdOrderByCreatedAtAsc(String conferenceId);

    List<VideoConferenceMember> findByPlayerAndStatusOrderByCreatedAtDesc(UUID player, String status);
}
