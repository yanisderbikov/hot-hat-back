package ru.hothat.ai;

import org.springframework.data.jpa.repository.JpaRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface AiVoiceSessionRepository extends JpaRepository<AiVoiceSession,String> {
    Optional<AiVoiceSession> findByRoomIdAndGameNumberAndTurnKey(
        String roomId,Integer gameNumber,String turnKey);
    List<AiVoiceSession> findByRoomIdAndGameNumberOrderByCreatedAtDesc(
        String roomId,Integer gameNumber);
    List<AiVoiceSession> findByCreatedAtGreaterThanEqualOrderByCreatedAtDesc(Instant since);
    List<AiVoiceSession> findByStatusIn(List<String> statuses);
}
