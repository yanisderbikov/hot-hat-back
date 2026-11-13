package ru.hothat.ai;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface AiVoiceWordMemoryRepository extends JpaRepository<AiVoiceWordMemory,String> {
    boolean existsByRoomIdAndGameNumberAndTurnIdAndActionId(
        String roomId,Integer gameNumber,String turnId,String actionId);
    List<AiVoiceWordMemory> findTop40ByRoomIdAndGameNumberAndTeamIdOrderByRecordedAtDesc(
        String roomId,Integer gameNumber,String teamId);
}
