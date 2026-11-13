package ru.hothat.ai;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.hothat.config.ApiException;
import ru.hothat.game.domain.MatchEngine;
import ru.hothat.game.domain.MatchPhase;
import ru.hothat.game.domain.MatchState;
import ru.hothat.game.domain.TurnStarted;
import ru.hothat.game.domain.WordResolved;
import ru.hothat.game.domain.TurnRules;
import ru.hothat.game.store.MatchSession;
import ru.hothat.game.store.MatchStore;
import ru.hothat.repository.GetterRoom;

import java.util.*;
import java.util.regex.Pattern;

/**
 * Server-authoritative game tools ONLY for a currently dispatched AI seat.
 * No arbitrary model-provided word, score, countdown, team or role is trusted.
 */
@Service @RequiredArgsConstructor
public class AiVoiceGameService {
    private final MatchStore matches;
    private final AiVoiceUsageService usage;
    private final MatchEngine engine;
    private final GetterRoom rooms;

    private record Verified(AiVoiceSession usage,MatchState game,String humanUid) {}
    private Verified verify(String sessionId) {
        AiVoiceSession u=usage.byId(sessionId)
                .orElseThrow(()->ApiException.of("AI_SESSION_NOT_FOUND",404));
        if (!List.of("running","pending").contains(u.getStatus()))
            throw ApiException.of("AI_VOICE_SESSION_INACTIVE",409);
        MatchState state=matches.read(u.getRoomId());
        if ("smalltalk".equals(u.getMode())) {
            if(u.getSpeakerUid()==null || u.getSpeakerUid().isBlank() ||
                    rooms.getPlayer(u.getRoomId(),u.getSpeakerUid()).isEmpty() ||
                    rooms.getPlayer(u.getRoomId(),u.getBotUid()).isEmpty() ||
                    !Objects.equals(state.getGameNumber(),u.getGameNumber()))
                throw ApiException.of("AI_SMALLTALK_NOT_IN_ROOM",403);
            return new Verified(u,state,u.getSpeakerUid());
        }
        if (state.isRanked() || state.getGameNumber()!=u.getGameNumber()
                || !Objects.equals(state.getCurrentTeamId(),u.getTeamId())
                || !List.of(MatchPhase.TURN_INTRO,MatchPhase.ACTIVE).contains(state.getPhase()))
            throw ApiException.of("AI_NOT_ACTIVE_TEAM",409);
        List<String> roster=state.rosterOf(u.getTeamId());
        if (roster.size()!=2 || !roster.contains(u.getBotUid()))
            throw ApiException.of("AI_NOT_IN_GAME_ROSTER",403);
        String other=roster.stream().filter(id->!id.equals(u.getBotUid())).findFirst().orElse("");
        if (AiVoiceSeatPolicy.isAi(other) || other.startsWith("testbot-"))
            throw ApiException.of("AI_NEEDS_HUMAN_PARTNER",403);
        return new Verified(u,state,other);
    }

    @Transactional(readOnly=true)
    public Map<String,Object> context(String sessionId) {
        Verified valid=verify(sessionId);
        MatchState g=valid.game();
        AiVoiceSession u=valid.usage();
        boolean smalltalk="smalltalk".equals(u.getMode());
        boolean aiExplaining=!smalltalk && g.getPhase()==MatchPhase.ACTIVE
                && Objects.equals(g.getExplainerUid(),u.getBotUid());
        boolean aiGuessing=!smalltalk && g.getPhase()==MatchPhase.ACTIVE
                && Objects.equals(g.getGuesserUid(),u.getBotUid());
        Map<String,Object> view=new LinkedHashMap<>();
        view.put("roomId",u.getRoomId());
        view.put("gameNumber",g.getGameNumber());
        view.put("phase",g.getPhase().name().toLowerCase(java.util.Locale.ROOT));
        view.put("botUid",u.getBotUid());
        view.put("humanUid",valid.humanUid());
        view.put("isPaused",g.isPaused());
        view.put("mode",smalltalk?"smalltalk":"game");
        view.put("role",smalltalk?"smalltalk":aiExplaining?"explainer":aiGuessing?"guesser":"unselected");
        view.put("turnId",g.getTurnId());
        view.put("deadlineMs",g.getPhase()==MatchPhase.ACTIVE?TurnRules.deadline(g):0);
        view.put("currentWord",aiExplaining?g.getCurrentWord():null);
        view.put("wordsLeft",g.getWordsLeft());
        view.put("teamActive",true);
        return view;
    }

    @Transactional
    public Map<String,Object> aiChoosesToExplain(String sessionId) {
        Verified valid=verify(sessionId);
        if("smalltalk".equals(valid.usage().getMode()))
            throw ApiException.of("AI_SMALLTALK_GAME_ACTION_FORBIDDEN",403);
        if (valid.game().isPaused())throw ApiException.of("AI_GAME_PAUSED",409);
        MatchSession session=matches.open(valid.usage().getRoomId());
        MatchState game=session.state();
        if(game.isRanked() || !Objects.equals(game.getCurrentTeamId(),valid.usage().getTeamId()))
            throw ApiException.of("AI_NOT_ACTIVE_TEAM",409);
        TurnStarted started=engine.beginTurn(game,valid.usage().getBotUid());
        session.commit();
        return Map.of("ok",true,"outcome",started.outcome().name(),"role","explainer");
    }

    /**
     * Exact inflected-form match: ignore casing and separators only. Do not
     * accept stems, synonyms or substring matches (кот != котик). This does
     * NOT assert perfect speech recognition; unknown speaker never scores.
     */
    public static boolean exactWordSpoken(String target,String transcript) {
        String expected=wordsOnly(target);
        String observed=wordsOnly(transcript);
        return !expected.isBlank() && (" "+observed+" ").contains(" "+expected+" ");
    }

    private static String wordsOnly(String text) {
        if(text==null)return "";
        return text.toLowerCase(Locale.ROOT).replace('ё','е')
                .replaceAll("[^\\p{L}\\p{Nd}]+"," ").trim()
                .replaceAll(" +"," ");
    }

    /**
     * Transcript comes from the INPUT transcription stream, not from the LLM.
     * The speaker identity must be the one genuine human teammate.
     */
    @Transactional
    public Map<String,Object> observedSpeech(String sessionId,String speakerUid,
                                             String transcript,String wordActionId) {
        Verified valid=verify(sessionId);
        if("smalltalk".equals(valid.usage().getMode()))
            throw ApiException.of("AI_SMALLTALK_GAME_ACTION_FORBIDDEN",403);
        if (!valid.humanUid().equals(speakerUid))return Map.of("matched",false,"reason","OTHER_SPEAKER");
        MatchState snapshot=valid.game();
        if(snapshot.getPhase()!=MatchPhase.ACTIVE ||
                !valid.usage().getBotUid().equals(snapshot.getExplainerUid()))
            return Map.of("matched",false,"reason","AI_NOT_EXPLAINING");
        if (!exactWordSpoken(snapshot.getCurrentWord(),transcript))
            return Map.of("matched",false,"reason","FORM_NOT_EXACT");
        if (!wordActionId.matches("^[A-Za-z0-9_-]{6,80}$"))
            throw ApiException.of("AI_WORD_ACTION_ID_INVALID",400);
        MatchSession txn=matches.open(valid.usage().getRoomId());
        MatchState game=txn.state();
        if (!Objects.equals(snapshot.getTurnId(),game.getTurnId()) ||
                !Objects.equals(snapshot.getCurrentWord(),game.getCurrentWord()) ||
                game.isPaused())throw ApiException.of("AI_STALE_WORD",409);
        String teamId=game.getCurrentTeamId();
        WordResolved resolved=engine.guess(game,game.getTurnId(),wordActionId);
        txn.addTeamScore(teamId,resolved.teamScoreDelta());
        txn.commit();
        return Map.of("matched",true,"action","guessed","word",resolved.word()==null?"":resolved.word(),
                "turnClosed",resolved.turnClosed());
    }

    @Transactional
    public Map<String,Object> skipOnHumanRequest(String sessionId,String speakerUid,
                                         String transcript,String wordActionId) {
        Verified valid=verify(sessionId);
        if("smalltalk".equals(valid.usage().getMode()))
            throw ApiException.of("AI_SMALLTALK_GAME_ACTION_FORBIDDEN",403);
        if (!valid.humanUid().equals(speakerUid))
            throw ApiException.of("AI_ONLY_HUMAN_TEAMMATE_CAN_SKIP",403);
        String spoken=wordsOnly(transcript);
        if(!Pattern.compile("(?:^| )(?:пропусти|пропускай|пропускаем|следующее слово|давай другое|переходи к следующему)(?: |$)")
                .matcher(spoken).find())
            throw ApiException.of("AI_SKIP_REQUEST_NOT_CONFIRMED",409);
        if (!wordActionId.matches("^[A-Za-z0-9_-]{6,80}$"))
            throw ApiException.of("AI_WORD_ACTION_ID_INVALID",400);
        MatchSession txn=matches.open(valid.usage().getRoomId());
        MatchState game=txn.state();
        if(game.getPhase()!=MatchPhase.ACTIVE || game.isPaused() ||
                !valid.usage().getBotUid().equals(game.getExplainerUid()))
            throw ApiException.of("AI_NOT_EXPLAINING",409);
        WordResolved resolved=engine.skip(game,game.getTurnId(),wordActionId);
        txn.commit();
        return Map.of("skipped",true,"turnClosed",resolved.turnClosed());
    }
}
