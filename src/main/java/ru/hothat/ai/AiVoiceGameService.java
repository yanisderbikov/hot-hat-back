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
    private final AiVoiceWordMemoryService wordMemory;

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
        if (other.startsWith("testbot-")) {
            // Scripted partners exist only in admin test rooms. They never
            // supply a microphone track; the runner acts on their behalf.
            // Check the authoritative room test-bot list AND the saved seat.
            ru.hothat.model.room.Room room=rooms.getById(u.getRoomId())
                    .orElseThrow(()->ApiException.of("AI_TEST_ROOM_REQUIRED",403));
            ru.hothat.model.room.RoomPlayer teammate=rooms.getPlayer(u.getRoomId(),other)
                    .orElseThrow(()->ApiException.of("AI_SCRIPTED_PARTNER_MISSING",403));
            if(!Boolean.TRUE.equals(room.getIsTestRoom())
                    || room.getTestBotIds()==null || !room.getTestBotIds().contains(other)
                    || !Boolean.TRUE.equals(teammate.getIsTestBot())
                    || AiVoiceSeatPolicy.isAi(teammate.getUid()))
                throw ApiException.of("AI_NEEDS_HUMAN_PARTNER",403);
        }
        if(AiVoiceSeatPolicy.isAi(other)){
            // Only an actual two-AI roster, already validated when OWNER placed
            // the seats, is allowed. Human voices from other teams cannot score.
            if(rooms.getPlayer(u.getRoomId(),other).isEmpty()
                    || rooms.getPlayer(u.getRoomId(),u.getBotUid()).isEmpty())
                throw ApiException.of("AI_DUO_ROSTER_INVALID",403);
        }
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
        AiVoiceCharacters.Character character=AiVoiceCharacters.byName(
                rooms.getPlayer(u.getRoomId(),u.getBotUid())
                        .map(ru.hothat.model.room.RoomPlayer::getName).orElse("Рублиша"));
        view.put("characterId",character.id());
        view.put("characterName",character.name());
        view.put("voice",character.voice());
        view.put("personality",character.personality());
        view.put("humanUid",valid.humanUid()); // legacy key: linked teammate identity
        boolean duo=AiVoiceSeatPolicy.isAi(valid.humanUid());
        view.put("aiDuo",duo);
        view.put("scriptedPartner",!smalltalk && valid.humanUid().startsWith("testbot-"));
        view.put("designatedExplainerUid",duo && !g.rosterOf(u.getTeamId()).isEmpty()
                ? g.rosterOf(u.getTeamId()).get(0) : "");
        view.put("isPaused",g.isPaused());
        view.put("mode",smalltalk?"smalltalk":"game");
        view.put("role",smalltalk?"smalltalk":aiExplaining?"explainer":aiGuessing?"guesser":"unselected");
        view.put("turnId",g.getTurnId());
        view.put("deadlineMs",g.getPhase()==MatchPhase.ACTIVE?TurnRules.deadline(g):0);
        view.put("currentWord",aiExplaining?g.getCurrentWord():null);
        view.put("wordsLeft",g.getWordsLeft());
        view.put("teamActive",!smalltalk);
        // Only previously resolved (never upcoming/secret) words from this AI team.
        view.put("recentWords",wordMemory.recent(u.getRoomId(),g.getGameNumber(),u.getTeamId()));
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
        if(AiVoiceSeatPolicy.isAi(valid.humanUid())){
            List<String> duoRoster=game.rosterOf(valid.usage().getTeamId());
            // The server, not either LLM, chooses a single speaker.
            // Without this, guesser could also invoke choose_to_explain,
            // creating two competing LiveKit turn roles.
            if(duoRoster.isEmpty() || !Objects.equals(
                    duoRoster.get(0),valid.usage().getBotUid()))
                throw ApiException.of("AI_DUO_GUESSER_CANNOT_EXPLAIN",403);
        }
        TurnStarted started=engine.beginTurn(game,valid.usage().getBotUid());
        session.commit();
        return Map.of("ok",true,"outcome",started.outcome().name(),"role","explainer");
    }

    /**
     * A voice guess must contain the whole actual word or word sequence.
     * Russian inflection is NOT a different game answer: "космонавта",
     * "путешествия", "снежного человека" count for the nominative card.
     * Never use substring/stem matching (кот != котик).
     */
    public static boolean exactWordSpoken(String target,String transcript) {
        String expected=wordsOnly(target);
        String observed=wordsOnly(transcript);
        if(expected.isBlank() || observed.isBlank())return false;
        String[] need=expected.split(" ");
        String[] spoken=observed.split(" ");
        if(spoken.length<need.length)return false;
        for(int offset=0;offset+need.length<=spoken.length;offset++) {
            boolean matched=true;
            for(int j=0;j<need.length;j++) {
                if(!sameSpokenRussianForm(need[j],spoken[offset+j])) {
                    matched=false;
                    break;
                }
            }
            if(matched)return true;
        }
        return false;
    }

    private static boolean sameSpokenRussianForm(String lemma,String heard) {
        if(lemma.equals(heard))return true;
        // Keep unfamiliar scripts and numerals strictly literal.
        if(!lemma.matches("[а-я]+") || !heard.matches("[а-я]+")
                || lemma.length()<3)return false;
        // Adjectives: снежный -> снежного; секретный -> секретному,
        // синий -> синего; avoid accepting arbitrary shared stems.
        if(lemma.length()>4 && (lemma.endsWith("ый")||lemma.endsWith("ий")
                ||lemma.endsWith("ой"))) {
            String stem=lemma.substring(0,lemma.length()-2);
            String endings=lemma.endsWith("ий")
                    ? "его ему им ем яя ее ие их ими юю ей"
                    : "ого ому ым ом ая ое ые ых ыми ую ой";
            for(String ending:endings.split(" "))
                if(heard.equals(stem+ending))return true;
            return false;
        }
        if(lemma.endsWith("ие")) {
            String stem=lemma.substring(0,lemma.length()-2);
            for(String ending:List.of("ия","ию","ием","ии","ий","иям","иями","иях"))
                if(heard.equals(stem+ending))return true;
            return false;
        }
        String stem=lemma;
        List<String> endings=List.of();
        if(lemma.endsWith("а")) {
            stem=lemma.substring(0,lemma.length()-1);
            endings=List.of("ы","и","е","у","ой","ою","ам","ами","ах");
        } else if(lemma.endsWith("я")) {
            stem=lemma.substring(0,lemma.length()-1);
            endings=List.of("и","е","ю","ей","ею","ям","ями","ях");
        } else if(lemma.endsWith("о")) {
            stem=lemma.substring(0,lemma.length()-1);
            endings=List.of("а","у","ом","е","ам","ами","ах");
        } else if(lemma.endsWith("е")) {
            stem=lemma.substring(0,lemma.length()-1);
            endings=List.of("я","ю","ем","и","й","ям","ями","ях");
        } else if(lemma.endsWith("ь")) {
            stem=lemma.substring(0,lemma.length()-1);
            endings=List.of("я","ю","ем","е","и","ей","ью","ям","ями","ях");
        } else if(lemma.matches(".*[бвгджзйклмнпрстфхцчшщ]")) {
            endings=List.of("а","у","ом","е","ы","и","ов","ев","ам","ами","ах");
        }
        for(String ending:endings)
            if(heard.equals(stem+ending))return true;
        return false;
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
        // An utterance may reach this transaction after the final-word grace
        // period. TURN_EXPIRED is NOT a correct counted guess, even if the
        // speech happens to contain the right word. Never say "Верно!" for 0.
        if (resolved.outcome()!=WordResolved.Outcome.COUNTED) {
            if (resolved.outcome()==WordResolved.Outcome.TURN_EXPIRED)txn.commit();
            return Map.of("matched",false,"reason","NOT_COUNTED",
                    "turnClosed",resolved.turnClosed());
        }
        txn.addTeamScore(teamId,resolved.teamScoreDelta());
        txn.commit();
        wordMemory.record(valid.usage().getRoomId(),game.getGameNumber(),teamId,
                game.getTurnId(),wordActionId,resolved.word(),"guessed",true);
        return Map.of("matched",true,"action","guessed","word",resolved.word()==null?"":resolved.word(),
                "turnClosed",resolved.turnClosed());
    }

    /**
     * An authenticated AI's OWN completed spoken answer is authoritative
     * evidence of its utterance in either an AI+AI OR human+AI game.
     * Only the server-authorized GUESSER may submit it; the explainer's
     * secret word is never accepted as a self-scored guess.
     *
     * A model claiming "correct" is never evidence: require an exact match
     * to the current server secret word and the current live turn.
     */
    @Transactional
    public Map<String,Object> guessedByDuetVoice(String sessionId,
                                                 String transcript,String actionId) {
        Verified valid=verify(sessionId);
        MatchState snapshot=valid.game();
        AiVoiceSession u=valid.usage();
        if (snapshot.getPhase()!=MatchPhase.ACTIVE
                || snapshot.isPaused()
                || !Objects.equals(snapshot.getGuesserUid(),u.getBotUid())
                || !Objects.equals(snapshot.getExplainerUid(),valid.humanUid()))
            return Map.of("matched",false,"reason","AI_GUESSER_ONLY");
        if (!exactWordSpoken(snapshot.getCurrentWord(),transcript))
            return Map.of("matched",false,"reason","FORM_NOT_EXACT");
        if (actionId==null || !actionId.matches("^[A-Za-z0-9_-]{6,80}$"))
            throw ApiException.of("AI_WORD_ACTION_ID_INVALID",400);
        MatchSession txn=matches.open(u.getRoomId());
        MatchState game=txn.state();
        if (!Objects.equals(snapshot.getTurnId(),game.getTurnId())
                || !Objects.equals(snapshot.getCurrentWord(),game.getCurrentWord())
                || game.isPaused() || game.getPhase()!=MatchPhase.ACTIVE)
            return Map.of("matched",false,"reason","WORD_ADVANCED");
        String teamId=game.getCurrentTeamId();
        WordResolved resolved=engine.guess(game,game.getTurnId(),actionId);
        if (resolved.outcome()!=WordResolved.Outcome.COUNTED) {
            if (resolved.outcome()==WordResolved.Outcome.TURN_EXPIRED)txn.commit();
            return Map.of("matched",false,"reason","NOT_COUNTED",
                    "turnClosed",resolved.turnClosed());
        }
        txn.addTeamScore(teamId,resolved.teamScoreDelta());
        txn.commit();
        wordMemory.record(u.getRoomId(),game.getGameNumber(),teamId,
                game.getTurnId(),actionId,resolved.word(),"guessed",true);
        return Map.of("matched",true,"action","guessed",
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
        if(!explicitSkipRequested(transcript))
            throw ApiException.of("AI_SKIP_REQUEST_NOT_CONFIRMED",409);
        if (!wordActionId.matches("^[A-Za-z0-9_-]{6,80}$"))
            throw ApiException.of("AI_WORD_ACTION_ID_INVALID",400);
        MatchSession txn=matches.open(valid.usage().getRoomId());
        MatchState game=txn.state();
        if(game.getPhase()!=MatchPhase.ACTIVE || game.isPaused() ||
                !valid.usage().getBotUid().equals(game.getExplainerUid()))
            throw ApiException.of("AI_NOT_EXPLAINING",409);
        String teamId=game.getCurrentTeamId();
        WordResolved resolved=engine.skip(game,game.getTurnId(),wordActionId);
        txn.commit();
        wordMemory.record(valid.usage().getRoomId(),game.getGameNumber(),teamId,
                game.getTurnId(),wordActionId,resolved.word(),"skipped",
                resolved.outcome()==WordResolved.Outcome.SKIPPED);
        return Map.of("skipped",true,"turnClosed",resolved.turnClosed());
    }

    static boolean explicitSkipRequested(String transcript) {
        String spoken=wordsOnly(transcript);
        return Pattern.compile("(?:^| )(?:пропусти|пропускай|пропускаем|следующее слово|след слово|давай след слово|давай следующее слово|давай следующее|следующую карточку|давай другое|давай новое слово|переходи к следующему|перейдем к следующему слову)(?: |$)")
                .matcher(spoken).find();
    }
}
