package ru.hothat.game.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import ru.hothat.support.Matches;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static ru.hothat.support.Matches.ANN;
import static ru.hothat.support.Matches.BLUE;
import static ru.hothat.support.Matches.BOB;
import static ru.hothat.support.Matches.CAT;
import static ru.hothat.support.Matches.DAN;
import static ru.hothat.support.Matches.RED;

/** Кто голосует по спорным словам и когда слово считается отменённым. */
class AppealRulesTest {

    /** Закрытый ход красных с двумя засчитанными словами. */
    private MatchState judgedRedTurn() {
        MatchState state = Matches.room();
        state.setTeamOrder(List.of(RED, BLUE));
        state.setRosters(Matches.rosters());
        state.setPhase(MatchPhase.APPEAL);
        state.setCurrentTeamId(RED);
        state.setLastTurn(LastTurn.opened("turn_1", RED, 2,
                List.of(GuessedWord.guessed("w1", "небо"), GuessedWord.guessed("w2", "море")), ANN, BOB));
        return state;
    }

    private static Map<String, List<String>> votes(String uid, String... wordIds) {
        Map<String, List<String>> votes = new LinkedHashMap<>();
        votes.put(uid, List.of(wordIds));
        return votes;
    }

    @Test
    @DisplayName("Большинство — это больше половины имеющих право: при двоих нужны двое, при троих двое")
    void majorityIsMoreThanHalf() {
        assertThat(AppealRules.majority(1)).isEqualTo(1);
        assertThat(AppealRules.majority(2)).isEqualTo(2);
        assertThat(AppealRules.majority(3)).isEqualTo(2);
        assertThat(AppealRules.majority(4)).isEqualTo(3);
        assertThat(AppealRules.majority(5)).isEqualTo(3);
    }

    @Test
    @DisplayName("Право голоса есть у всех, кроме команды, чей ход разбирается")
    void judgedTeamHasNoVote() {
        MatchState state = judgedRedTurn();

        assertThat(AppealRules.eligible(state)).containsExactly(CAT, DAN);
        assertThat(AppealRules.mayVote(state, ANN)).isFalse();
        assertThat(AppealRules.mayVote(state, CAT)).isTrue();
    }

    @Test
    @DisplayName("Разбирается команда закрытого хода, а не та, чья очередь сейчас")
    void judgedTeamComesFromClosedTurn() {
        MatchState state = judgedRedTurn();
        state.setCurrentTeamId(BLUE);

        assertThat(AppealRules.judgedTeamId(state)).isEqualTo(RED);
    }

    @Test
    @DisplayName("Без закрытого хода разбирается текущая команда")
    void withoutClosedTurnCurrentTeamIsJudged() {
        MatchState state = judgedRedTurn();
        state.setLastTurn(null);

        assertThat(AppealRules.judgedTeamId(state)).isEqualTo(RED);
    }

    @Test
    @DisplayName("Голос игрока разбираемой команды не считается, даже если он записан")
    void voteOfJudgedPlayerIsIgnored() {
        MatchState state = judgedRedTurn();
        Map<String, List<String>> votes = votes(ANN, "w1");
        votes.put(CAT, List.of("w1"));
        state.setAppealVotes(votes);

        assertThat(AppealRules.votesFor(state, "w1")).isEqualTo(1);
        assertThat(AppealRules.invalidWords(state)).isEmpty();
    }

    @Test
    @DisplayName("Слово отменяется, когда за него набралось большинство имеющих право")
    void majorityCancelsTheWord() {
        MatchState state = judgedRedTurn();
        Map<String, List<String>> votes = votes(CAT, "w1");
        votes.put(DAN, List.of("w1"));
        state.setAppealVotes(votes);

        assertThat(AppealRules.votesFor(state, "w1")).isEqualTo(2);
        assertThat(AppealRules.invalidWords(state))
                .extracting(GuessedWord::id).containsExactly("w1");
    }

    @Test
    @DisplayName("Молчание не отменяет слово: без голосов отменять нечего")
    void silenceCancelsNothing() {
        MatchState state = judgedRedTurn();

        assertThat(AppealRules.invalidWords(state)).isEmpty();
    }
}
