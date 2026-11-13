package ru.hothat.ai;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.hothat.config.ApiException;
import ru.hothat.config.HotHatUser;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Bill estimate, bounded allocation and history (the OpenAI model's true
 * invoice is owned by LiveKit; this is an explicitly labelled estimate).
 * No transcript/audio/secret word is persisted here.
 */
@Service @RequiredArgsConstructor
public class AiVoiceUsageService {
    private static final BigDecimal ZERO=BigDecimal.ZERO;
    private static final BigDecimal VOICE_USD_PER_SECOND=new BigDecimal("0.000833333333");
    private static final BigDecimal SESSION_RESERVATION_USD=new BigDecimal("0.50");

    private final AiVoiceSessionRepository sessions;

    @Value("${hot-hat.gpt-live.monthly-budget-usd:5.00}")
    private BigDecimal monthlyBudget;
    @Value("${hot-hat.gpt-live.input-usd-per-million:0}")
    private BigDecimal inputRate;
    @Value("${hot-hat.gpt-live.output-usd-per-million:0}")
    private BigDecimal outputRate;

    public static Instant monthStart(Instant time){
        ZonedDateTime day=time.atZone(ZoneOffset.UTC);
        return day.withDayOfMonth(1).toLocalDate().atStartOfDay(ZoneOffset.UTC).toInstant();
    }

    private BigDecimal cost(AiVoiceSession s){
        BigDecimal voice=s.getVoiceSeconds().multiply(VOICE_USD_PER_SECOND);
        BigDecimal input=BigDecimal.valueOf(s.getBackendInputTokens())
                .multiply(inputRate).divide(BigDecimal.valueOf(1_000_000),9,RoundingMode.HALF_UP);
        BigDecimal output=BigDecimal.valueOf(s.getBackendOutputTokens())
                .multiply(outputRate).divide(BigDecimal.valueOf(1_000_000),9,RoundingMode.HALF_UP);
        return voice.add(input).add(output).setScale(6,RoundingMode.HALF_UP);
    }

    private boolean pending(AiVoiceSession s){
        return "pending".equals(s.getStatus()) || "running".equals(s.getStatus());
    }

    private List<AiVoiceSession> monthRows(){
        return sessions.findByCreatedAtGreaterThanEqualOrderByCreatedAtDesc(monthStart(Instant.now()));
    }

    private BigDecimal reservedPlusSpent(List<AiVoiceSession> rows){
        return rows.stream().map(s->s.getEstimatedCostUsd().add(
                pending(s)?SESSION_RESERVATION_USD:ZERO))
                .reduce(ZERO,BigDecimal::add);
    }

    public Map<String,Object> budget(){
        List<AiVoiceSession> rows=monthRows();
        BigDecimal spent=rows.stream().map(AiVoiceSession::getEstimatedCostUsd)
                .reduce(ZERO,BigDecimal::add);
        BigDecimal reserved=reservedPlusSpent(rows).subtract(spent);
        BigDecimal remaining=monthlyBudget.subtract(spent).subtract(reserved).max(ZERO);
        return Map.of("month",monthStart(Instant.now()).toString(),
                "budgetUsd",monthlyBudget,"estimatedSpentUsd",spent,
                "reservedUsd",reserved,"estimatedRemainingUsd",remaining,
                "isEstimate",true,"providerBalanceVerified",false,
                "unpricedBackendTokens",inputRate.signum()==0 || outputRate.signum()==0);
    }

    @Transactional
    public AiVoiceSession reserve(String roomId,int gameNumber,String teamId,String botUid,String turnKey) {
        Optional<AiVoiceSession> existing=sessions.findByRoomIdAndGameNumberAndTurnKey(roomId,gameNumber,turnKey);
        if(existing.isPresent())return existing.get();
        List<AiVoiceSession> rows=monthRows();
        // The first version deliberately allows one paid session globally.
        // Avoid concurrent unbounded usage while LiveKit pricing isn't verified.
        if(rows.stream().anyMatch(this::pending))
            throw ApiException.of("AI_VOICE_SESSION_ALREADY_RUNNING",409);
        if(reservedPlusSpent(rows).add(SESSION_RESERVATION_USD).compareTo(monthlyBudget)>0)
            throw ApiException.of("AI_MONTHLY_BUDGET_EXHAUSTED",402);
        AiVoiceSession session=AiVoiceSession.builder()
                .sessionId("ai_"+UUID.randomUUID().toString().replace("-",""))
                .roomId(roomId).gameNumber(gameNumber).teamId(teamId)
                .botUid(botUid).turnKey(turnKey)
                .status("pending").createdAt(Instant.now()).build();
        return sessions.save(session);
    }

    @Transactional
    public void markDispatched(String id,String dispatchId){
        AiVoiceSession s=sessions.findById(id)
                .orElseThrow(()->ApiException.of("AI_SESSION_NOT_FOUND",404));
        if("finished".equals(s.getStatus()) || "failed".equals(s.getStatus()))return;
        s.setLivekitDispatchId(dispatchId);
        s.setStatus("running");
        s.setStartedAt(Instant.now());
        sessions.save(s);
    }

    @Transactional
    public Map<String,Object> report(String id,double seconds,long input,long output,boolean finished,String errorCode){
        AiVoiceSession s=sessions.findById(id)
                .orElseThrow(()->ApiException.of("AI_SESSION_NOT_FOUND",404));
        if(!"finished".equals(s.getStatus()) && !"failed".equals(s.getStatus())){
            BigDecimal time=BigDecimal.valueOf(Math.max(0,Math.min(180.0,seconds)));
            s.setVoiceSeconds(s.getVoiceSeconds().max(time));
            s.setBackendInputTokens(Math.max(s.getBackendInputTokens(),Math.max(0,input)));
            s.setBackendOutputTokens(Math.max(s.getBackendOutputTokens(),Math.max(0,output)));
            s.setEstimatedCostUsd(cost(s));
            s.setLastUsageAt(Instant.now());
            if(finished){
                s.setStatus(errorCode==null || errorCode.isBlank()?"finished":"failed");
                s.setFinishedAt(Instant.now());
                s.setErrorCode(errorCode==null?null:errorCode.substring(0,Math.min(120,errorCode.length())));
            }
            sessions.save(s);
        }
        return summary(s);
    }

    public Optional<AiVoiceSession> byId(String id){return sessions.findById(id);}

    public Map<String,Object> summary(AiVoiceSession s){
        Map<String,Object> m=new LinkedHashMap<>();
        m.put("sessionId",s.getSessionId());m.put("roomId",s.getRoomId());
        m.put("gameNumber",s.getGameNumber());m.put("teamId",s.getTeamId());
        m.put("botUid",s.getBotUid());m.put("status",s.getStatus());
        m.put("voiceSeconds",s.getVoiceSeconds());
        m.put("backendInputTokens",s.getBackendInputTokens());
        m.put("backendOutputTokens",s.getBackendOutputTokens());
        m.put("estimatedCostUsd",s.getEstimatedCostUsd());
        m.put("createdAt",s.getCreatedAt());
        m.put("finishedAt",s.getFinishedAt());
        m.put("isEstimate",true);
        return m;
    }

    @Transactional(readOnly=true)
    public Map<String,Object> ownerHistory(HotHatUser user){
        if(user==null || !user.owner())throw ApiException.of("AI_OWNER_ONLY",403);
        List<AiVoiceSession> all=monthRows();
        Map<String,List<AiVoiceSession>> games=all.stream().collect(Collectors.groupingBy(
            s->s.getRoomId()+":"+s.getGameNumber(),LinkedHashMap::new,Collectors.toList()));
        List<Map<String,Object>> history=new ArrayList<>();
        games.forEach((id,rows)->{
            Map<String,Object> game=new LinkedHashMap<>();
            game.put("roomId",rows.get(0).getRoomId());
            game.put("gameNumber",rows.get(0).getGameNumber());
            game.put("createdAt",rows.get(0).getCreatedAt());
            game.put("voiceSeconds",rows.stream().map(AiVoiceSession::getVoiceSeconds).reduce(ZERO,BigDecimal::add));
            game.put("estimatedCostUsd",rows.stream().map(AiVoiceSession::getEstimatedCostUsd).reduce(ZERO,BigDecimal::add));
            game.put("sessions",rows.stream().map(this::summary).toList());
            history.add(game);
        });
        return Map.of("budget",budget(),"games",history,"isEstimate",true);
    }

    @Transactional(readOnly=true)
    public Map<String,Object> ownerGame(HotHatUser user,String roomId,int gameNumber){
        if(user==null || !user.owner())throw ApiException.of("AI_OWNER_ONLY",403);
        List<AiVoiceSession> rows=sessions.findByRoomIdAndGameNumberOrderByCreatedAtDesc(roomId,gameNumber);
        BigDecimal sum=rows.stream().map(AiVoiceSession::getEstimatedCostUsd)
                .reduce(ZERO,BigDecimal::add);
        return Map.of("roomId",roomId,"gameNumber",gameNumber,
                "estimatedCostUsd",sum,"sessions",rows.stream().map(this::summary).toList(),
                "budget",budget(),"isEstimate",true);
    }
}
