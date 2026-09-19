package ru.hothat.game.domain;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Кто голосует по спорным словам и когда слово считается отменённым.
 *
 * <p>Голосуют все игроки партии, кроме команды, чей ход разбирается: судить
 * собственный ход — не голосование. Большинство считается от числа имеющих
 * право, а не от числа проголосовавших: иначе один голос при девяти
 * молчащих отменял бы слово.
 */
public final class AppealRules {

    private AppealRules() {
    }

    /** Команда, чей ход разбирается: у закрытого хода она своя, не текущая. */
    public static String judgedTeamId(MatchState state) {
        LastTurn lastTurn = state.getLastTurn();
        String teamId = lastTurn == null ? null : lastTurn.teamId();
        return teamId == null || teamId.isBlank() ? state.getCurrentTeamId() : teamId;
    }

    /** Все игроки партии за вычетом разбираемой команды. */
    public static List<String> eligible(MatchState state) {
        List<String> judged = state.rosterOf(judgedTeamId(state));
        List<String> eligible = new ArrayList<>();
        for (String uid : state.allPlayers()) {
            if (!judged.contains(uid)) {
                eligible.add(uid);
            }
        }
        return eligible;
    }

    public static boolean mayVote(MatchState state, String uid) {
        return eligible(state).contains(uid);
    }

    /** Больше половины имеющих право: при четверых — трое, при двоих — двое. */
    public static int majority(int eligibleCount) {
        return eligibleCount / 2 + 1;
    }

    /** Сколько голосов подано за отмену этого слова. */
    public static int votesFor(MatchState state, String wordId) {
        int count = 0;
        Map<String, List<String>> votes = state.getAppealVotes();
        for (String uid : eligible(state)) {
            List<String> mine = votes.get(uid);
            if (mine != null && mine.contains(wordId)) {
                count++;
            }
        }
        return count;
    }

    /** Слова, за отмену которых набралось большинство. */
    public static List<GuessedWord> invalidWords(MatchState state) {
        LastTurn lastTurn = state.getLastTurn();
        if (lastTurn == null || lastTurn.guessedWords() == null) {
            return List.of();
        }
        int majority = majority(eligible(state).size());
        List<GuessedWord> invalid = new ArrayList<>();
        for (GuessedWord word : lastTurn.guessedWords()) {
            if (votesFor(state, word.id()) >= majority) {
                invalid.add(word);
            }
        }
        return invalid;
    }
}
