package ru.hothat.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import ru.hothat.common.livekit.LiveKitClient;
import ru.hothat.config.ApiException;
import ru.hothat.config.HotHatUser;
import ru.hothat.model.room.Room;
import ru.hothat.model.room.RoomPlayer;
import ru.hothat.repository.GetterRoom;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Owner-triggered, short, metered voice chat independent of turn order.
 * Each session is tied to one authenticated room member and a real AI seat.
 */
@Slf4j @Service @RequiredArgsConstructor
public class AiVoiceSmallTalkService {
    private final GetterRoom rooms;
    private final AiVoiceUsageService usage;
    private final LiveKitClient livekit;
    private final ObjectMapper mapper;

    @Value("${hot-hat.gpt-live.enabled:false}") private boolean featureEnabled;
    @Value("${hot-hat.gpt-live.dispatch-enabled:false}") private boolean dispatchEnabled;
    @Value("${hot-hat.gpt-live.worker-secret:}") private String workerSecret;

    private Room requireAccess(HotHatUser owner,String roomId,String botUid) {
        if(owner==null || !owner.owner())throw ApiException.of("AI_SMALLTALK_OWNER_ONLY",403);
        Room room=rooms.getById(roomId).orElseThrow(()->ApiException.of("ROOM_NOT_FOUND",404));
        if(room.isClosed())throw ApiException.of("ROOM_CLOSED",409);
        if(!AiVoiceSeatPolicy.isAi(botUid) ||
                rooms.getPlayer(roomId,botUid).isEmpty())throw ApiException.of("AI_NOT_IN_ROOM",404);
        RoomPlayer speaker=rooms.getPlayer(roomId,owner.uid()).orElseThrow(
                ()->ApiException.of("AI_SMALLTALK_REQUIRES_PLAYER_MIC",403));
        if(Boolean.TRUE.equals(speaker.getIsTestBot()))
            throw ApiException.of("AI_SMALLTALK_REQUIRES_PLAYER_MIC",403);
        return room;
    }

    public Map<String,Object> status(HotHatUser user,String roomId,String botUid) {
        requireAccess(user,roomId,botUid);
        return usage.activeSessions().stream()
                .filter(s->"smalltalk".equals(s.getMode())
                        && s.getRoomId().equals(roomId) && s.getBotUid().equals(botUid)
                        && user.uid().equals(s.getSpeakerUid()))
                .findFirst().map(s->Map.<String,Object>of("active",
                        !"stopping".equals(s.getStatus()),"status",s.getStatus(),
                        "sessionId",s.getSessionId(),"mode","smalltalk"))
                .orElseGet(()->Map.of("active",false,"status","idle","mode","smalltalk"));
    }

    public Map<String,Object> start(HotHatUser owner,String roomId,String botUid) {
        Room room=requireAccess(owner,roomId,botUid);
        String aiTeam=rooms.getPlayer(roomId,botUid).map(RoomPlayer::getTeamId).orElse("");
        // The normal game dispatcher already talks on the AI team's turn.
        if(List.of("turnIntro","active","appeal").contains(room.getPhase())
                && aiTeam.equals(room.getCurrentTeamId()))
            throw ApiException.of("AI_SMALLTALK_NOT_NEEDED_ACTIVE_TEAM",409);
        if(!featureEnabled || !dispatchEnabled || workerSecret==null || workerSecret.length()<32)
            throw ApiException.of("AI_VOICE_NOT_READY",503);
        List<AiVoiceSession> pending=usage.activeSessions();
        if(!pending.isEmpty()) {
            AiVoiceSession active=pending.get(0);
            if("smalltalk".equals(active.getMode()) && roomId.equals(active.getRoomId())
                    && botUid.equals(active.getBotUid()) && owner.uid().equals(active.getSpeakerUid()))
                return status(owner,roomId,botUid);
            throw ApiException.of("AI_VOICE_SESSION_ALREADY_RUNNING",409);
        }
        int gameNumber=room.getGameNumber()==null?0:room.getGameNumber();
        String key="smalltalk:"+UUID.randomUUID().toString().replace("-","");
        AiVoiceSession created=usage.reserve(roomId,gameNumber,
                rooms.getPlayer(roomId,botUid).map(RoomPlayer::getTeamId).orElse(""),
                botUid,key,owner.uid());
        try {
            boolean greet=usage.shouldGreet(roomId,gameNumber,created.getSessionId());
            String metadata=mapper.writeValueAsString(Map.of(
                    "sessionId",created.getSessionId(),"roomId",roomId,
                    "botUid",botUid,"mode","smalltalk","speakerUid",owner.uid(),
                    "gameNumber",gameNumber,"shouldGreet",greet,"maxSessionSeconds",180));
            Map<String,Object> result=livekit.createAiDispatch(roomId,metadata);
            usage.markDispatched(created.getSessionId(),String.valueOf(result.getOrDefault("id","")));
            log.info("Rublisha owner smalltalk dispatched room={} session={}",roomId,created.getSessionId());
        }catch(Exception e){
            usage.failDispatch(created.getSessionId(),"DISPATCH_FAILED");
            log.warn("Rublisha smalltalk dispatch failed room={}: {}",roomId,e.getMessage());
            throw ApiException.of("AI_SMALLTALK_DISPATCH_FAILED",502);
        }
        return status(owner,roomId,botUid);
    }

    public Map<String,Object> stop(HotHatUser owner,String roomId,String botUid) {
        requireAccess(owner,roomId,botUid);
        for(AiVoiceSession session:usage.activeSessions()){
            if(!"smalltalk".equals(session.getMode()) ||
                    !roomId.equals(session.getRoomId()) ||
                    !botUid.equals(session.getBotUid()) ||
                    !owner.uid().equals(session.getSpeakerUid()))continue;
            usage.beginStopping(session.getSessionId());
            try{livekit.deleteAiDispatch(roomId,session.getLivekitDispatchId());}
            catch(RuntimeException e){log.warn("Rublisha smalltalk stop failed: {}",e.getMessage());}
        }
        return Map.of("active",false,"status","stopping","mode","smalltalk");
    }
}
