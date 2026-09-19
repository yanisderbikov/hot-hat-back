package ru.hothat.game.usecase;

import ru.hothat.game.api.dto.TurnClosingOutcome;
import ru.hothat.game.domain.TurnClosing;

/** Перевод исхода закрытия хода в слово ответа: один на кнопку и на часы. */
final class TurnClosings {

    private TurnClosings() {
    }

    static TurnClosingOutcome outcome(TurnClosing.Outcome outcome) {
        return switch (outcome) {
            case CLOSED -> TurnClosingOutcome.CLOSED;
            case ALREADY_CLOSED -> TurnClosingOutcome.ALREADY_CLOSED;
            case NOT_YET -> TurnClosingOutcome.NOT_YET;
        };
    }
}
