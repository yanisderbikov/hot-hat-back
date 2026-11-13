package ru.hothat.ai;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

/**
 * Durable history of successfully resolved, server-confirmed words in a
 * single room/game/team. Never includes the unrevealed bag/current word,
 * speech transcripts, or any other team's secret information.
 */
@Service @RequiredArgsConstructor
public class AiVoiceWordMemoryService {
    private final AiVoiceWordMemoryRepository repository;

    @Transactional
    public void record(String roomId,int gameNumber,String teamId,String turnId,
                       String actionId,String word,String outcome,boolean confirmed) {
        if(!confirmed || roomId==null || roomId.isBlank() || gameNumber<=0 ||
                teamId==null || teamId.isBlank() || turnId==null || turnId.isBlank() ||
                actionId==null || actionId.isBlank() || word==null || word.isBlank() ||
                !List.of("guessed","skipped").contains(outcome))return;
        if(repository.existsByRoomIdAndGameNumberAndTurnIdAndActionId(
                roomId,gameNumber,turnId,actionId))return;
        repository.save(AiVoiceWordMemory.builder()
                .id(UUID.randomUUID().toString()).roomId(roomId)
                .gameNumber(gameNumber).teamId(teamId).turnId(turnId)
                .actionId(actionId).word(word).outcome(outcome).build());
    }

    @Transactional(readOnly=true)
    public List<Map<String,String>> recent(String roomId,int gameNumber,String teamId) {
        if(roomId==null || teamId==null || gameNumber<=0)return List.of();
        return repository.findTop40ByRoomIdAndGameNumberAndTeamIdOrderByRecordedAtDesc(
                roomId,gameNumber,teamId).stream()
                .map(entry -> Map.of("word",entry.getWord(),
                    "outcome",entry.getOutcome(),"turnId",entry.getTurnId(),
                    "actionId",entry.getActionId()))
                .toList();
    }
}
