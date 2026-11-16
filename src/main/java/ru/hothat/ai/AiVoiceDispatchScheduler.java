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
import java.util.concurrent.atomic.AtomicLong;

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
    private final AtomicLong lastBlockedWarningMs = new AtomicLong(0L);

    @Value("${hot-hat.gpt-live.enabled:false}")
    private boolean featureEnabled;

    @Value("${hot-hat.gpt-live.dispatch-enabled:false}")
    private boolean dispatchEnabled;

    @Value("${hot-hat.gpt-live.worker-secret:}")
    private String workerSecret;

    /** Report configuration issues once at startup, not silently every 3 seconds.
     * No credentials, token values, or model prompts are logged.
     */
    @jakarta.annotation.PostConstruct
    void logConfiguration() {
        boolean ready=featureEnabled && dispatchEnabled
                && workerSecret!=null && workerSecret.length()>=32;
        if(ready) log.info("GPT-Live dispatcher READY: enabled=true, named dispatch, 180s cap");
        else log.warn("GPT-Live dispatcher DISABLED: featureEnabled={}, dispatchEnabled={}, workerSecretReady={}",
                featureEnabled,dispatchEnabled,workerSecret!=null && workerSecret.length()>=32);
    }

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

    private List<String> aiUidsForActiveTeam(Room room) {
        List<String> roster=GameRules.rosterForTeam(room,room.getCurrentTeamId());
        if(roster.size()!=2)return List.of();
        // Frozen match rosters MUST agree with the actual room seat records.
        // Never dispatch a bot from another team because currentTeamId or
        // room.teamRosters lagged behind a team swap.
        String activeTeam=room.getCurrentTeamId();
        List<RoomPlayer> seats=new ArrayList<>(2);
        for(String uid:roster){
            Optional<RoomPlayer> row=rooms.getPlayer(room.getId(),uid);
            if(row.isEmpty() || !Objects.equals(activeTeam,row.get().getTeamId())){
                log.warn("AI voice dispatch blocked: mismatched roster room={} team={}",
                        room.getId(),activeTeam);
                return List.of();
            }
            seats.add(row.get());
        }
        List<String> bots=roster.stream().filter(AiVoiceSeatPolicy::isAi).toList();
        if(bots.size()==2){
            // Two AI seats only if BOTH actual seats belong to current team.
            return bots;
        }
        if(bots.size()!=1)return List.of();
        String human=roster.stream().filter(uid->!uid.equals(bots.get(0))).findFirst().orElse("");
        RoomPlayer row=seats.get(roster.indexOf(human));
        if(human.startsWith("testbot-")) {
            // The test-bot has no microphone. LiveKit worker uses muted-input
            // mode and the existing test runner generates the simulated guess.
            boolean scripted=Boolean.TRUE.equals(room.getIsTestRoom())
                    && room.getTestBotIds()!=null && room.getTestBotIds().contains(human)
                    && Boolean.TRUE.equals(row.getIsTestBot());
            return scripted ? bots : List.of();
        }
        if(!AiVoiceSeatPolicy.isHuman(row))return List.of();
        return bots;
    }

    private String aiUidForActiveTeam(Room room) {
        List<String> ids=aiUidsForActiveTeam(room);
        return ids.isEmpty()?null:ids.get(0);
    }

    private void launch(Room room) {
        List<String> bots=aiUidsForActiveTeam(room);
        if(bots.isEmpty())return;
        boolean duet=bots.size()==2;
        Map<String,Object> prior=Json.map(room.getLastTurn());
        String previousId=Json.str(prior.get("turnId"));
        String roundKey=room.getCurrentTeamId()+":"+
                (previousId.isBlank() ? "first" : previousId);
        // If a GPT-Live duet deadlocks and the worker reports it as FAILED,
        // permit ONE new paid session per bot this SAME turn, only if enough
        // seconds remain. The usual monthly budget / global concurrency
        // checks still apply. Never restart unrelated teams or human sessions.
        List<AiVoiceSession> turnSessions=duet ? usage.gameSessions(room.getId(),room.getGameNumber())
                .stream().filter(s->bots.contains(s.getBotUid())
                        && s.getTurnKey()!=null
                        && s.getTurnKey().startsWith(roundKey+":")).toList()
                : List.of();
        boolean stalledDuet=duet && turnSessions.stream().anyMatch(s->
                "failed".equals(s.getStatus()) && List.of(
                        "DUET_DEADLOCK","DUET_OPENING_FAILED","DUET_OPENING_SILENT",
                        "DUET_DISPATCH_FAILED","DUET_PARTNER_UNAVAILABLE"
                ).contains(s.getErrorCode()));
        long deadline=GameRules.currentTurnDeadline(room);
        boolean enoughTime="turnIntro".equals(room.getPhase())
                || ("active".equals(room.getPhase())
                    && deadline>System.currentTimeMillis()+16000L);
        boolean oneRecovery=stalledDuet && enoughTime;
        if(duet){
            // Reserve BOTH seats and BOTH budget holds atomically BEFORE
            // dispatching either. Never start a lone paid worker when the
            // second worker would exceed the monthly cap.
            List<AiVoiceSession> current=usage.activeSessions();
            if(!current.isEmpty()){
                // Both seats were already launched on a prior reconcile.
                if(current.size()==2 && current.stream().allMatch(s->
                        "game".equals(s.getMode()) && room.getId().equals(s.getRoomId())
                        && Objects.equals(room.getGameNumber(),s.getGameNumber())
                        && room.getCurrentTeamId().equals(s.getTeamId())
                        && bots.contains(s.getBotUid())))return;
                warnBlocked("GPT-Live duet dispatch deferred: other or incomplete voice session");
                return;
            }
            List<String> keys=bots.stream().map(botUid->
                    roundKey+":"+botUid+(oneRecovery ? ":recovery1" : "")).toList();
            List<AiVoiceSession> pair=usage.reserveDuo(
                    room.getId(),room.getGameNumber(),room.getCurrentTeamId(),bots,keys);
            List<AiVoiceSession> dispatched=new ArrayList<>(2);
            for(AiVoiceSession allocated:pair){
                if(!"pending".equals(allocated.getStatus()))continue;
                if(oneRecovery)
                    log.warn("GPT-Live bounded duet recovery room={} team={} bot={} attempt=1",
                            room.getId(),room.getCurrentTeamId(),allocated.getBotUid());
                try {
                    String metadata=mapper.writeValueAsString(Map.of(
                        "sessionId",allocated.getSessionId(),
                        "roomId",room.getId(),
                        "botUid",allocated.getBotUid(),
                        "teamId",room.getCurrentTeamId(),
                        "gameNumber",room.getGameNumber(),
                        "shouldGreet",usage.shouldGreet(
                                room.getId(),room.getGameNumber(),allocated.getBotUid(),
                                allocated.getSessionId()),
                        "maxSessionSeconds",180));
                    Map<String,Object> dispatch=livekit.createAiDispatch(room.getId(),metadata);
                    String dispatchId=Json.str(dispatch.get("id"));
                    if(dispatchId.isBlank())throw new IllegalStateException("Missing LiveKit dispatch ID");
                    usage.markDispatched(allocated.getSessionId(),dispatchId);
                    allocated.setLivekitDispatchId(dispatchId);
                    dispatched.add(allocated);
                    log.info("GPT-Live duet voice dispatched room={} team={} bot={} session={}",
                            room.getId(),room.getCurrentTeamId(),allocated.getBotUid(),
                            allocated.getSessionId());
                }catch(Exception error){
                    usage.failDispatch(allocated.getSessionId(),"DUET_DISPATCH_FAILED");
                    // The partner is already reserved but not running.
                    for(AiVoiceSession other:pair){
                        if(!other.getSessionId().equals(allocated.getSessionId())
                                && dispatched.stream().noneMatch(s->
                                    s.getSessionId().equals(other.getSessionId())))
                            usage.failDispatch(other.getSessionId(),"DUET_PARTNER_UNAVAILABLE");
                    }
                    // If the first dispatch succeeded but the second failed,
                    // stop the first immediately rather than paying for silence.
                    for(AiVoiceSession started:dispatched){
                        try {
                            livekit.deleteAiDispatch(room.getId(),
                                    started.getLivekitDispatchId());
                        }catch(RuntimeException stopError){
                            log.warn("GPT-Live orphaned duet dispatch stop failed: {}",
                                    stopError.getClass().getSimpleName());
                        }
                        usage.beginStopping(started.getSessionId());
                    }
                    log.warn("GPT-Live duet pairing failed room={} bot={} cause={}",
                            room.getId(),allocated.getBotUid(),
                            error.getClass().getSimpleName());
                    return;
                }
            }
            return;
        }

        // Non-duet human+AI and scripted-pair sessions remain single-agent.
        for(String botUid:bots){
            List<AiVoiceSession> active=usage.activeSessions();
            boolean alreadyRunning=active.stream().anyMatch(s->
                    room.getId().equals(s.getRoomId())
                    && Objects.equals(room.getGameNumber(),s.getGameNumber())
                    && botUid.equals(s.getBotUid()));
            if(alreadyRunning)continue;
            if(!active.isEmpty()){
                warnBlocked("GPT-Live dispatch blocked room="+room.getId()
                        +" by existing voice session status="+active.get(0).getStatus());
                return;
            }
            AiVoiceSession allocated=usage.reserve(
                    room.getId(),room.getGameNumber(),room.getCurrentTeamId(),
                    botUid,roundKey);
            if(!"pending".equals(allocated.getStatus()))continue;
            try {
                String metadata=mapper.writeValueAsString(Map.of(
                    "sessionId",allocated.getSessionId(),
                    "roomId",room.getId(),
                    "botUid",botUid,
                    "teamId",room.getCurrentTeamId(),
                    "gameNumber",room.getGameNumber(),
                    "shouldGreet",usage.shouldGreet(
                            room.getId(),room.getGameNumber(),botUid,allocated.getSessionId()),
                    "maxSessionSeconds",180));
                Map<String,Object> dispatch=livekit.createAiDispatch(room.getId(),metadata);
                String dispatchId=Json.str(dispatch.get("id"));
                usage.markDispatched(allocated.getSessionId(),dispatchId);
                log.info("GPT-Live voice dispatched room={} game={} team={} bot={} session={}",
                        room.getId(),room.getGameNumber(),room.getCurrentTeamId(),
                        botUid,allocated.getSessionId());
            }catch(Exception error){
                usage.failDispatch(allocated.getSessionId(),"DISPATCH_FAILED");
                log.warn("GPT-Live explicit dispatch failed for room {} bot {}: {}",
                        room.getId(),botUid,error.getMessage());
            }
        }
    }

    private void warnBlocked(String message) {
        long now=System.currentTimeMillis();
        long previous=lastBlockedWarningMs.get();
        if(now-previous>30_000 && lastBlockedWarningMs.compareAndSet(previous,now))
            log.warn("{}",message);
    }

    private void stopInactive() {
        for(AiVoiceSession session:usage.activeSessions()){
            Room room=rooms.getById(session.getRoomId()).orElse(null);
            boolean smalltalk="smalltalk".equals(session.getMode());
            boolean keep=smalltalk
                    ? room!=null && !room.isClosed()
                        && Objects.equals(session.getGameNumber(),room.getGameNumber())
                        && rooms.getPlayer(room.getId(),session.getBotUid()).isPresent()
                        && rooms.getPlayer(room.getId(),session.getSpeakerUid()).isPresent()
                        // A turn suppresses off-turn chat for ALL AI seats in
                        // the room, regardless of whose team is playing.
                        && !List.of("turnIntro","active","appeal").contains(room.getPhase())
                    : eligible(room) && aiUidsForActiveTeam(room).contains(session.getBotUid())
                        && Objects.equals(session.getTeamId(),room.getCurrentTeamId())
                        && Objects.equals(session.getGameNumber(),room.getGameNumber());
            boolean expired=session.getStartedAt()!=null
                    && session.getStartedAt().isBefore(Instant.now().minusSeconds(205));
            if(keep && !expired && !"stopping".equals(session.getStatus()))continue;
            if(!"stopping".equals(session.getStatus())){
                try {livekit.deleteAiDispatch(session.getRoomId(),session.getLivekitDispatchId());}
                catch(RuntimeException e){log.warn("GPT-Live dispatch stop failed: {}",e.getMessage());}
                usage.beginStopping(session.getSessionId());
            }
            // Worker normally finishes and reports actual usage. Avoid a
            // permanent lock if the worker crashed before reporting.
            if("stopping".equals(session.getStatus()) &&
                ((session.getLastUsageAt()!=null &&
                   session.getLastUsageAt().isBefore(Instant.now().minusSeconds(45)))
                 || (session.getStartedAt()!=null &&
                     session.getStartedAt().isBefore(Instant.now().minusSeconds(240))))) {
                usage.report(session.getSessionId(),
                    session.getVoiceSeconds().doubleValue(),
                    session.getBackendInputTokens(),
                    session.getBackendOutputTokens(),true,"WORKER_REPORT_TIMEOUT");
            }
        }
    }
}
