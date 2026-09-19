package ru.hothat.game.usecase;

import ru.hothat.game.api.dto.WordActionOutcome;
import ru.hothat.game.domain.WordResolved;

/** Перевод исхода движка в слово ответа: один на «угадали» и «пропустили». */
final class WordActions {

    private WordActions() {
    }

    static WordActionOutcome outcome(WordResolved.Outcome outcome) {
        return switch (outcome) {
            case COUNTED -> WordActionOutcome.COUNTED;
            case SKIPPED -> WordActionOutcome.SKIPPED;
            case REPEATED -> WordActionOutcome.REPEATED;
            case TURN_EXPIRED -> WordActionOutcome.TURN_EXPIRED;
        };
    }
}
