package ru.hothat.game.domain;

import ru.hothat.config.ApiException;

import java.time.Clock;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Движок партии: продвижение хода, шляпа, счёт, апелляция, пауза.
 *
 * <p>Это те самые правила, которые до переезда жили в браузере — в
 * транзакциях {@code beginTurn()}, {@code guessed()}, {@code skipWord()},
 * {@code endTurn()}, {@code nextTurn()} и {@code appealTurnData()}
 * ({@code app-core.js:12987-13560}). Там их исполнял тот же человек, который
 * играет: клиент решал, истёк ли ход, какое слово выпадет следующим и сколько
 * очков записать команде. Здесь решает сервер.
 *
 * <p>Ни Spring, ни базы, ни собственного времени и жребия: часы и случайность
 * приходят в конструктор. Поэтому весь ход проверяется тестом без базы —
 * с часами, которые двигает тест, и жребием, который тест назначает.
 *
 * <p>Каждая операция хода идемпотентна по паре «идентификатор хода —
 * идентификатор слова». Клиент повторяет запрос при потере связи, и без этого
 * повтор давал бы два очка за одно слово либо два перехода хода (риск 2 плана).
 */
public final class MatchEngine {

    private final Clock clock;
    private final RandomSource random;

    public MatchEngine(Clock clock, RandomSource random) {
        this.clock = clock;
        this.random = random;
    }

    private long now() {
        return clock.millis();
    }

    // ───────────────────────── начало партии ─────────────────────────

    /**
     * Старт партии: составы и имена замораживаются, слова перемешиваются.
     *
     * <p>Заморозка — не оптимизация. Игрок может выйти посреди партии, а очередь
     * ходов, право голоса в апелляции и подписи в протоколе должны остаться
     * теми же, с какими партия началась.
     */
    public void start(MatchState state, List<String> order, Map<String, List<String>> rosters,
                      Map<String, String> names, List<String> words) {
        state.setTeamOrder(new ArrayList<>(order));
        state.setRosters(new LinkedHashMap<>(rosters));
        state.setPlayerNames(new LinkedHashMap<>(names));
        state.setGameNumber(state.getGameNumber() + 1);
        state.setPhase(MatchPhase.TURN_INTRO);
        state.setBag(new ArrayList<>(random.shuffle(words)));
        state.setWordsLeft(words.size());
        state.setCurrentWord(null);
        state.setCurrentTeamIndex(0);
        state.setCurrentTeamId(order.isEmpty() ? null : order.get(0));
        clearTurn(state);
        clearAppeal(state);
        clearPause(state);
        state.setTermination(null);
        state.setLastTurn(null);
        state.getLocks().clear();
        state.setClips(new LinkedHashMap<>());
        state.setLastEvent(null);
        state.setRecentEvents(new ArrayList<>());
        state.setSpecialProgressByTeam(new LinkedHashMap<>());
        state.setSpecialCursorByTeam(new LinkedHashMap<>());
    }

    // ───────────────────────── ход ─────────────────────────

    /** Начать ход: объясняющий тянет первое слово, часы идут от этой секунды. */
    public TurnStarted beginTurn(MatchState state, String uid) {
        requireNotPaused(state);
        if (state.getPhase() == MatchPhase.ACTIVE) {
            if (uid.equals(state.getExplainerUid())) {
                // Повтор при потере связи: ход уже идёт, второе слово тянуть нельзя.
                return new TurnStarted(TurnStarted.Outcome.ALREADY_RUNNING, state.getTurnId(),
                        state.getCurrentWord(), TurnRules.deadline(state), TurnRules.durationSeconds(state));
            }
            throw ApiException.of("TURN_ALREADY_STARTED", 409);
        }
        if (state.getPhase() != MatchPhase.TURN_INTRO) {
            throw ApiException.of("ROUND_NOT_ACTIVE", 409);
        }
        List<String> roster = state.activeRoster();
        if (roster.size() != 2) {
            throw ApiException.of("TEAMS_NOT_READY", 409);
        }
        if (!roster.contains(uid)) {
            throw ApiException.of("TURN_NOT_YOURS", 403);
        }
        if (state.bagEmpty()) {
            finish(state);
            return new TurnStarted(TurnStarted.Outcome.MATCH_FINISHED, null, null, 0, 0);
        }

        String guesserUid = state.partnerOf(state.getCurrentTeamId(), uid);
        String word = draw(state);
        long startedAt = now();
        state.setPhase(MatchPhase.ACTIVE);
        state.setTurnId(TurnIds.turn(random));
        state.setExplainerUid(uid);
        state.setExplainerName(state.nameOf(uid));
        state.setGuesserUid(guesserUid);
        state.setGuesserName(state.nameOf(guesserUid));
        state.setCurrentWord(word);
        state.setCurrentTurnScore(0);
        state.setGuessedWords(new ArrayList<>());
        state.setTurnStartedAtMs(startedAt);
        state.setTurnDurationSeconds(state.getDefaultTurnDurationSeconds());
        state.setLegacyTurnEndsAt(0);
        state.setLastGuessedWord(null);
        state.setLastSkippedWord(null);
        state.setLastActionType(null);
        state.setLastActionWord(null);
        state.setLastActionAtMs(0);
        state.setLastTurn(null);
        state.setLastEvent(null);
        clearAppeal(state);
        clearPause(state);
        return new TurnStarted(TurnStarted.Outcome.STARTED, state.getTurnId(), word,
                TurnRules.deadline(state), TurnRules.durationSeconds(state));
    }

    /** Слово угадано: очко команде, следующее слово из шляпы. */
    public WordResolved guess(MatchState state, String turnId, String wordId) {
        return resolve(state, turnId, wordId, true);
    }

    /** Слово пропущено: возвращается в шляпу, очков не приносит. */
    public WordResolved skip(MatchState state, String turnId, String wordId) {
        return resolve(state, turnId, wordId, false);
    }

    private WordResolved resolve(MatchState state, String turnId, String wordId, boolean counted) {
        requireNotPaused(state);
        GuessedWord already = findWord(state, wordId);
        if (already != null) {
            // Повтор того же нажатия: слово уже разобрано, счёт не трогаем.
            return new WordResolved(WordResolved.Outcome.REPEATED, already.word(), state.getCurrentWord(),
                    state.getCurrentTurnScore(), state.getWordsLeft(), 0,
                    state.getPhase() != MatchPhase.ACTIVE, state.getAppealEndsAt());
        }
        requireRunningTurn(state, turnId);
        long moment = now();
        String word = state.getCurrentWord();
        if (word == null) {
            // Шляпа опустела ещё на прошлом нажатии, а ход остался открытым.
            TurnClosing closing = closeTurn(state, moment);
            return new WordResolved(WordResolved.Outcome.TURN_EXPIRED, null, null,
                    closing.preliminaryScore(), state.getWordsLeft(), 0, true, closing.appealEndsAt());
        }
        if (TurnRules.expired(state, moment)) {
            // Отсрочка последнего слова прошла: нажатие опоздало.
            TurnClosing closing = closeTurn(state, moment);
            return new WordResolved(WordResolved.Outcome.TURN_EXPIRED, word, null,
                    closing.preliminaryScore(), state.getWordsLeft(), 0, true, closing.appealEndsAt());
        }

        boolean lastWord = TurnRules.inLastWordGrace(state, moment);
        int teamScoreDelta = 0;
        List<GuessedWord> words = new ArrayList<>(state.getGuessedWords());
        if (counted) {
            words.add(GuessedWord.guessed(wordId, word));
            state.setCurrentTurnScore(state.getCurrentTurnScore() + 1);
            state.setLastGuessedWord(word);
            state.setLastActionType("guessed");
            teamScoreDelta = 1;
        } else {
            words.add(GuessedWord.skipped(wordId, word));
            state.getBag().add(word);
            state.setLastSkippedWord(word);
            state.setLastActionType("skip");
        }
        state.setGuessedWords(words);
        state.setLastActionWord(word);
        state.setLastActionAtMs(moment);

        String nextWord = null;
        if (!lastWord && !state.bagEmpty()) {
            nextWord = draw(state);
        }
        state.setCurrentWord(nextWord);
        state.setWordsLeft(state.getBag().size() + (nextWord == null ? 0 : 1));

        if (nextWord == null) {
            // Слов больше нет либо истекла отсрочка — ход закрывается сразу.
            TurnClosing closing = closeTurn(state, moment);
            return new WordResolved(counted ? WordResolved.Outcome.COUNTED : WordResolved.Outcome.SKIPPED,
                    word, null, closing.preliminaryScore(), state.getWordsLeft(), teamScoreDelta,
                    true, closing.appealEndsAt());
        }
        return new WordResolved(counted ? WordResolved.Outcome.COUNTED : WordResolved.Outcome.SKIPPED,
                word, nextWord, state.getCurrentTurnScore(), state.getWordsLeft(), teamScoreDelta,
                false, 0);
    }

    /** Завершить ход по кнопке объясняющего. */
    public TurnClosing complete(MatchState state, String turnId) {
        requireNotPaused(state);
        TurnClosing repeated = alreadyClosed(state, turnId);
        if (repeated != null) {
            return repeated;
        }
        requireRunningTurn(state, turnId);
        return closeTurn(state, now());
    }

    /**
     * Завершить ход по серверным часам.
     *
     * <p>Зовёт любой участник: у объясняющего мог закрыться браузер ровно на
     * последней секунде, и тогда партия зависла бы навсегда. Время считает
     * сервер, поэтому «поторопить» ход отсюда нельзя.
     */
    public TurnClosing expire(MatchState state, String turnId) {
        TurnClosing repeated = alreadyClosed(state, turnId);
        if (repeated != null) {
            return repeated;
        }
        requireRunningTurn(state, turnId);
        long moment = now();
        boolean bagExhausted = state.getCurrentWord() == null && state.bagEmpty();
        if (!bagExhausted && !TurnRules.expired(state, moment)) {
            return new TurnClosing(TurnClosing.Outcome.NOT_YET, state.getTurnId(), 0, state.getCurrentTurnScore());
        }
        return closeTurn(state, moment);
    }

    /**
     * Закрытие хода: слово возвращается в шляпу, объявляется голосование.
     *
     * <p>Текущее слово возвращается всегда — иначе оно пропадало бы вместе с
     * ходом, а шляпа должна опустеть ровно один раз.
     */
    private TurnClosing closeTurn(MatchState state, long moment) {
        List<String> bag = new ArrayList<>(state.getBag());
        if (state.getCurrentWord() != null) {
            bag.add(state.getCurrentWord());
        }
        state.setBag(new ArrayList<>(random.shuffle(bag)));
        state.setWordsLeft(state.getBag().size());
        state.setCurrentWord(null);

        List<GuessedWord> counted = state.getGuessedWords().stream().filter(GuessedWord::counted).toList();
        state.setLastTurn(LastTurn.opened(state.getTurnId(), state.getCurrentTeamId(), state.getCurrentTurnScore(),
                counted, state.getExplainerUid(), state.getGuesserUid()));
        state.setPhase(MatchPhase.APPEAL);
        state.setAppealEndsAt(moment + TurnRules.APPEAL_MS);
        state.setAppealVotes(new LinkedHashMap<>());
        state.setTurnStartedAtMs(null);
        state.setTurnDurationSeconds(null);
        state.setLegacyTurnEndsAt(0);
        state.setLastEvent(null);
        clearPause(state);
        return new TurnClosing(TurnClosing.Outcome.CLOSED, state.getLastTurn().turnId(),
                state.getAppealEndsAt(), state.getCurrentTurnScore());
    }

    /** Передать очередь следующей команде с экрана итогов. */
    public TurnAdvanced advance(MatchState state, String turnId) {
        requireNotPaused(state);
        LastTurn lastTurn = state.getLastTurn();
        if (state.getPhase() != MatchPhase.BETWEEN) {
            boolean sameTurn = lastTurn == null || turnId.equals(lastTurn.turnId());
            if (sameTurn && (state.getPhase() == MatchPhase.TURN_INTRO || state.getPhase() == MatchPhase.FINISHED)) {
                return new TurnAdvanced(TurnAdvanced.Outcome.ALREADY_ADVANCED, state.getPhase(),
                        state.getCurrentTeamId(), state.getWordsLeft());
            }
            throw ApiException.of("TURN_STALE", 409);
        }
        if (lastTurn != null && !turnId.equals(lastTurn.turnId())) {
            throw ApiException.of("TURN_STALE", 409);
        }
        state.setLastTurn(null);
        if (state.bagEmpty()) {
            finish(state);
            return new TurnAdvanced(TurnAdvanced.Outcome.MATCH_FINISHED, state.getPhase(),
                    state.getCurrentTeamId(), 0);
        }
        state.setPhase(MatchPhase.TURN_INTRO);
        clearTurn(state);
        return new TurnAdvanced(TurnAdvanced.Outcome.ADVANCED, state.getPhase(),
                state.getCurrentTeamId(), state.getWordsLeft());
    }

    // ───────────────────────── апелляция ─────────────────────────

    /** Подать или снять голос за отмену слова. */
    public AppealVoteCast vote(MatchState state, String uid, String wordId, boolean cancelWord) {
        if (state.getPhase() != MatchPhase.APPEAL) {
            throw ApiException.of("APPEAL_NOT_ACTIVE", 409);
        }
        requireNotPaused(state);
        if (now() > state.getAppealEndsAt()) {
            throw ApiException.of("APPEAL_CLOSED", 409);
        }
        if (!AppealRules.mayVote(state, uid)) {
            throw ApiException.of("APPEAL_NOT_ELIGIBLE", 403);
        }
        LastTurn lastTurn = state.getLastTurn();
        boolean known = lastTurn != null && lastTurn.guessedWords().stream()
                .anyMatch(word -> word.id().equals(wordId));
        if (!known) {
            throw ApiException.of("WORD_NOT_IN_TURN", 404);
        }
        Map<String, List<String>> votes = new LinkedHashMap<>(state.getAppealVotes());
        List<String> mine = new ArrayList<>(votes.getOrDefault(uid, List.of()));
        mine.remove(wordId);
        if (cancelWord) {
            mine.add(wordId);
        }
        votes.put(uid, mine);
        state.setAppealVotes(votes);
        int eligible = AppealRules.eligible(state).size();
        return new AppealVoteCast(List.copyOf(mine), eligible, AppealRules.votesFor(state, wordId),
                AppealRules.majority(eligible));
    }

    /**
     * Подвести итог хода: отменить слова, посчитать награды, передать очередь.
     *
     * <p>Пауза итогу не мешает, если её остаток времени голосования уже вышел:
     * иначе партия, поставленная на паузу во время апелляции, не смогла бы
     * продолжиться никогда.
     */
    public AppealClosed closeAppeal(MatchState state, String turnId) {
        LastTurn lastTurn = state.getLastTurn();
        if (lastTurn != null && lastTurn.settled() && turnId.equals(lastTurn.turnId())) {
            return new AppealClosed(AppealClosed.Outcome.ALREADY_SETTLED, lastTurn.turnId(), lastTurn.teamId(),
                    lastTurn.finalScore(), lastTurn.invalidWordIds(), List.of(), lastTurn.rewards(),
                    lastTurn.specialRewardsByUid(), List.of(), 0, state.getPhase() == MatchPhase.FINISHED);
        }
        if (state.getPhase() != MatchPhase.APPEAL || lastTurn == null) {
            throw ApiException.of("APPEAL_NOT_ACTIVE", 409);
        }
        if (!turnId.equals(lastTurn.turnId())) {
            throw ApiException.of("TURN_STALE", 409);
        }
        long moment = now();
        boolean pauseOutlivedAppeal = state.isPaused() && state.getPausedAppealRemainingMs() <= 0;
        if (state.isPaused() && !pauseOutlivedAppeal) {
            throw ApiException.of("GAME_PAUSED", 409);
        }
        if (!state.isPaused() && moment < state.getAppealEndsAt()) {
            return new AppealClosed(AppealClosed.Outcome.STILL_OPEN, lastTurn.turnId(), lastTurn.teamId(),
                    lastTurn.score(), List.of(), List.of(), Map.of(), Map.of(), List.of(), 0, false);
        }

        String teamId = AppealRules.judgedTeamId(state);
        List<GuessedWord> invalid = AppealRules.invalidWords(state);
        List<String> invalidIds = invalid.stream().map(GuessedWord::id).toList();
        List<String> returnedWords = invalid.stream().map(GuessedWord::word)
                .map(word -> word == null ? "" : word.trim())
                .filter(word -> !word.isEmpty()).toList();
        int finalScore = Math.max(0, lastTurn.score() - invalid.size());
        Map<String, Integer> rewards = RewardRules.forScore(finalScore);

        // Редкие награды идут по сквозному счёту команды за партию, а не за ход.
        int previous = state.getSpecialProgressByTeam().getOrDefault(teamId, 0);
        int next = previous + finalScore;
        List<String> specials = state.isTestRoom() ? List.of() : RewardRules.specialsBetween(previous, next);
        List<String> rewarded = state.humanRosterOf(teamId);
        RewardRules.SpecialGrant grant = RewardRules.distribute(specials, rewarded,
                state.getSpecialCursorByTeam().getOrDefault(teamId, 0));
        state.getSpecialProgressByTeam().put(teamId, next);
        state.getSpecialCursorByTeam().put(teamId, grant.nextCursor());

        List<String> bag = new ArrayList<>(state.getBag());
        bag.addAll(returnedWords);
        state.setBag(new ArrayList<>(random.shuffle(bag)));
        state.setWordsLeft(state.getBag().size());
        state.setLastTurn(lastTurn.finalized(finalScore, invalidIds, rewards, grant.byUid(), moment));

        boolean finished = state.bagEmpty();
        int nextIndex = state.getTeamOrder().isEmpty()
                ? 0 : (state.getCurrentTeamIndex() + 1) % state.getTeamOrder().size();
        state.setCurrentTeamIndex(nextIndex);
        state.setCurrentTeamId(finished || state.getTeamOrder().isEmpty()
                ? teamId : state.getTeamOrder().get(nextIndex));
        state.setPhase(finished ? MatchPhase.FINISHED : MatchPhase.TURN_INTRO);
        clearTurn(state);
        clearAppeal(state);
        state.setLastEvent(null);
        if (pauseOutlivedAppeal && !finished) {
            // Пауза пережила голосование: игрок так и не вернулся, партия ждёт дальше.
            state.setPausedTurnRemainingMs(0);
            state.setPausedAppealRemainingMs(0);
            if (state.getPauseStartedAtMs() == 0) {
                state.setPauseStartedAtMs(moment);
            }
        } else {
            clearPause(state);
        }
        return new AppealClosed(AppealClosed.Outcome.SETTLED, lastTurn.turnId(), teamId, finalScore, invalidIds,
                returnedWords, rewards, grant.byUid(), rewarded, -invalid.size(), finished);
    }

    // ───────────────────────── пауза ─────────────────────────

    /** Пауза хозяина комнаты: снять её может только он же. */
    public void pauseByHost(MatchState state) {
        requirePausablePhase(state);
        freeze(state, "host_paused", List.of(), List.of());
        state.setHostPaused(true);
    }

    /** Пауза из-за пропавших игроков: держится, пока они не вернулись. */
    public void pauseForMissing(MatchState state, List<String> missingUids, List<String> missingNames) {
        requirePausablePhase(state);
        freeze(state, "player_disconnected", missingUids, missingNames);
    }

    private void freeze(MatchState state, String reason, List<String> missingUids, List<String> missingNames) {
        long moment = now();
        boolean wasPaused = state.isPaused();
        state.setPaused(true);
        state.setPauseReason(reason);
        state.setPauseMissingUids(new ArrayList<>(missingUids));
        state.setPauseMissingNames(new ArrayList<>(missingNames));
        state.setPauseStartedAtMs(wasPaused && state.getPauseStartedAtMs() > 0
                ? state.getPauseStartedAtMs() : moment);
        if (wasPaused) {
            return;
        }
        // Остаток хода и голосования замораживается, чтобы вернуть его целиком.
        if (state.getPhase() == MatchPhase.ACTIVE) {
            state.setPausedTurnRemainingMs(TurnRules.remainingMs(state, moment));
            state.setTurnStartedAtMs(null);
            state.setLegacyTurnEndsAt(0);
        }
        if (state.getPhase() == MatchPhase.APPEAL) {
            state.setPausedAppealRemainingMs(Math.max(0, state.getAppealEndsAt() - moment));
            state.setAppealEndsAt(0);
        }
    }

    /**
     * Снять паузу и вернуть замороженный остаток времени.
     *
     * <p>Снятие паузы с непоставленной партии — не ошибка, а ничего.
     * Повторное нажатие «Продолжить» и повтор запроса при потере связи иначе
     * заводили бы часы заново от нулевого остатка, и идущий ход обрывался бы
     * через десятую долю секунды.
     */
    public void resume(MatchState state) {
        if (!state.isPaused()) {
            return;
        }
        long moment = now();
        MatchPhase phase = state.getPhase();
        long turnRemaining = Math.max(0, state.getPausedTurnRemainingMs());
        long appealRemaining = Math.max(0, state.getPausedAppealRemainingMs());
        clearPause(state);
        if (phase == MatchPhase.ACTIVE) {
            state.setTurnStartedAtMs(moment);
            // Ход продолжается ровно с остатка, а не с полной длительности.
            state.setTurnDurationSeconds(Math.max(0.1, turnRemaining / 1000.0));
            state.setLegacyTurnEndsAt(0);
        }
        if (phase == MatchPhase.APPEAL) {
            state.setAppealEndsAt(moment + appealRemaining);
        }
    }

    // ───────────────────────── конец партии ─────────────────────────

    public void finish(MatchState state) {
        state.setPhase(MatchPhase.FINISHED);
        state.setCurrentWord(null);
        state.setWordsLeft(state.getBag().size());
        clearTurn(state);
        clearAppeal(state);
        clearPause(state);
    }

    /** Техническое завершение: доигрывать некому. */
    public void terminate(MatchState state, TechnicalTermination termination) {
        finish(state);
        state.setTermination(termination);
    }

    // ───────────────────────── общее ─────────────────────────

    /** Слово из шляпы. Жребий — внешний: иначе партию нечем воспроизвести. */
    private String draw(MatchState state) {
        List<String> bag = state.getBag();
        if (bag.isEmpty()) {
            return null;
        }
        return bag.remove(random.nextInt(bag.size()));
    }

    private GuessedWord findWord(MatchState state, String wordId) {
        for (GuessedWord word : state.getGuessedWords()) {
            if (word.id().equals(wordId)) {
                return word;
            }
        }
        LastTurn lastTurn = state.getLastTurn();
        if (lastTurn != null && lastTurn.guessedWords() != null) {
            for (GuessedWord word : lastTurn.guessedWords()) {
                if (word.id().equals(wordId)) {
                    return word;
                }
            }
        }
        return null;
    }

    private TurnClosing alreadyClosed(MatchState state, String turnId) {
        LastTurn lastTurn = state.getLastTurn();
        if (state.getPhase() != MatchPhase.ACTIVE && lastTurn != null && turnId.equals(lastTurn.turnId())) {
            return new TurnClosing(TurnClosing.Outcome.ALREADY_CLOSED, lastTurn.turnId(),
                    state.getAppealEndsAt(), lastTurn.score());
        }
        return null;
    }

    private void requireRunningTurn(MatchState state, String turnId) {
        if (state.getPhase() != MatchPhase.ACTIVE) {
            throw ApiException.of("ROUND_NOT_ACTIVE", 409);
        }
        if (state.getTurnId() == null || !state.getTurnId().equals(turnId)) {
            // Клиент говорит о ходе, которого уже нет: перечитать и не повторять.
            throw ApiException.of("TURN_STALE", 409);
        }
    }

    private void requireNotPaused(MatchState state) {
        if (state.isPaused()) {
            throw ApiException.of("GAME_PAUSED", 409);
        }
    }

    private void requirePausablePhase(MatchState state) {
        if (!state.getPhase().pausable()) {
            throw ApiException.of("ROUND_NOT_ACTIVE", 409);
        }
    }

    private void clearTurn(MatchState state) {
        state.setTurnId(null);
        state.setExplainerUid(null);
        state.setExplainerName(null);
        state.setGuesserUid(null);
        state.setGuesserName(null);
        state.setCurrentWord(null);
        state.setCurrentTurnScore(0);
        state.setGuessedWords(new ArrayList<>());
        state.setTurnStartedAtMs(null);
        state.setTurnDurationSeconds(null);
        state.setLegacyTurnEndsAt(0);
        state.setLastGuessedWord(null);
        state.setLastSkippedWord(null);
        state.setLastActionType(null);
        state.setLastActionWord(null);
        state.setLastActionAtMs(0);
    }

    private void clearAppeal(MatchState state) {
        state.setAppealEndsAt(0);
        state.setAppealVotes(new LinkedHashMap<>());
    }

    private void clearPause(MatchState state) {
        state.setPaused(false);
        state.setHostPaused(false);
        state.setPauseReason(null);
        state.setPauseMissingUids(new ArrayList<>());
        state.setPauseMissingNames(new ArrayList<>());
        state.setPauseStartedAtMs(0);
        state.setPausedTurnRemainingMs(0);
        state.setPausedAppealRemainingMs(0);
    }
}
