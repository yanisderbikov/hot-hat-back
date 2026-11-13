package ru.hothat.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import ru.hothat.common.livekit.LiveKitClient;
import ru.hothat.model.room.Room;
import ru.hothat.model.room.RoomPlayer;
import ru.hothat.repository.GetterRoom;
import ru.hothat.service.game.GameRules;
import ru.hothat.util.Json;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Start the paid agent from turnIntro (role choice), stop as soon as this
 * AI team leaves active/intro. Default OFF until a worker has been deployed
 * and explicit budget + owner configuration are in place.
 */
@Slf4j @Component @RequiredArgsConstructor
public class AiVoiceDispatchScheduler {
    private final GetterRoom rooms;
    private final AiVoiceUsageService usage;
    private final LiveKitClient livekit;
    private final ObjectMapper mapper;
    private final AtomicBoolean running = new AtomicBoolean(false);

    @Value("${hot-hat.gpt-live.enabled:false}")
    private boolean featureEnabled;

    @Value("${hot-hat.gpt-live.dispatch-enabled:false}")
    private boolean dispatchEnabled;

    @Value("${hot-hat.gpt-live.worker-secret:}")
    private String workerSecret;

    @Scheduled(fixedDelay=3000,initialDelay=10000)
    public void reconcile() {
        if(!featureEnabled || !dispatchEnabled || workerSecret==null ||
                workerSecret.length()<32 || !running.compareAndSet(false,true))return;
        try {
            stopInactive();
            // No GPT worker is needed in a room until the AI's own team
            // becomes current. The bot's mic is silent for all other teams.
            List<Room> possible=new ArrayList<>();
            possible.addAll(rooms.getByPhase("turnIntro",100));
            possible.addAll(rooms.getByPhase("active",100));
            for(Room room:possible) {
                if(!eligible(room))continue;
                try { launch(room); }
                catch(RuntimeException e) {
                    log.warn("GPT-Live not dispatched room {}: {}",room.getId(),e.getMessage());
                }
            }
        } finally {running.set(false);}
    }

    private boolean eligible(Room room) {
        return room!=null && !Boolean.TRUE.equals(room.getRanked())
                && !Boolean.TRUE.equals(room.getGamePaused())
                && room.getGameNumber()!=null && room.getGameNumber()>0
                && room.getCurrentTeamId()!=null;
    }

    private String aiUidForActiveTeam(Room room) {
        List<String> roster=GameRules.rosterForTeam(room,room.getCurrentTeamId());
        if(roster.size()!=2)return null;
        String ai=roster.stream().filter(AiVoiceSeatPolicy::isAi).findFirst().orElse(null);
        if(ai==null)return null;
        String human=roster.stream().filter(uid->!uid.equals(ai)).findFirst().orElse("");
        if(AiVoiceSeatPolicy.isAi(human)||human.startsWith("testbot-"))return null;
        Optional<RoomPlayer> row=rooms.getPlayer(room.getId(),human);
        if(row.isEmpty() || !AiVoiceSeatPolicy.isHuman(row.get()))return null;
        return ai;
    }

    private void launch(Room room) {
        String ai=aiUidForActiveTeam(room);
        if(ai==null)return;
        List<AiVoiceSession> sessionRows=usage.activeSessions();
        // Never overlap paid voice sessions, including during STOP.
        if(!sessionRows.isEmpty())return;
        Map<String,Object> prior=Json.map(room.getLastTurn());
        String previousId=Json.str(prior.get("turnId"));
        String roundKey=room.getCurrentTeamId()+":"+
                (previousId.isBlank() ? "first" : previousId);
        AiVoiceSession allocated=usage.reserve(room.getId(),room.getGameNumber(),
                room.getCurrentTeamId(),ai,roundKey);
        if(!"pending".equals(allocated.getStatus()))return;
        try {
            String metadata=mapper.writeValueAsString(Map.of(
                "sessionId",allocated.getSessionId(),
                "roomId",room.getId(),
                "botUid",ai,
                "teamId",room.getCurrentTeamId(),
                "gameNumber",room.getGameNumber(),
                "maxSessionSeconds",180));
            Map<String,Object> dispatch=livekit.createAiDispatch(room.getId(),metadata);
            String dispatchId=Json.str(dispatch.get("id"));
            usage.markDispatched(allocated.getSessionId(),dispatchId);
            log.info("GPT-Live voice dispatched room={} game={} team={} session={}",
                    room.getId(),room.getGameNumber(),room.getCurrentTeamId(),allocated.getSessionId());
        } catch(Exception e) {
            usage.failDispatch(allocated.getSessionId(),"DISPATCH_FAILED");
            log.warn("GPT-Live explicit dispatch failed for room {}: {}",room.getId(),e.getMessage());
        }
    }

    private void stopInactive() {
        for(AiVoiceSession session:usage.activeSessions()){
            Room room=rooms.getById(session.getRoomId()).orElse(null);
            boolean keep=eligible(room) && aiUidForActiveTeam(room)!=null
                    && Objects.equals(session.getBotUid(),aiUidForActiveTeam(room))
                    && Objects.equals(session.getTeamId(),room.getCurrentTeamId())
                    && Objects.equals(session.getGameNumber(),room.getGameNumber());
            if(keep)continue;
            if(!"stopping".equals(session.getStatus())){
                try {livekit.deleteAiDispatch(session.getRoomId(),session.getLivekitDispatchId());}
                catch(RuntimeException e){log.warn("GPT-Live dispatch stop failed: {}",e.getMessage());}
                usage.beginStopping(session.getSessionId());
            }
            // Worker normally finishes and reports actual usage. Avoid a
            // permanent lock if the worker crashed before reporting.
            if("stopping".equals(session.getStatus()) &&
                session.getLastUsageAt()!=null &&
                session.getLastUsageAt().isBefore(Instant.now().minusSeconds(45))) {
                usage.report(session.getSessionId(),
                    session.getVoiceSeconds().doubleValue(),
                    session.getBackendInputTokens(),
                    session.getBackendOutputTokens(),true,"WORKER_REPORT_TIMEOUT");
            }
        }
    }
}
