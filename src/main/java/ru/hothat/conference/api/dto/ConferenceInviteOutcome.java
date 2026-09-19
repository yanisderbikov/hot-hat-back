package ru.hothat.conference.api.dto;

import com.fasterxml.jackson.annotation.JsonValue;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Чем кончилось приглашение.
 *
 * <p>Перечисление, а не два булевых поля: прежний ответ нёс
 * {@code already_member} и {@code already_pending}, и клиент решал, что
 * значит «оба false». Здесь исходов три и они исключают друг друга.
 */
@Schema(description = "Исход приглашения в видео-чат")
public enum ConferenceInviteOutcome {

    /** Приглашение отправлено: карточка появится у друга. */
    INVITED("invited"),
    /** Друг уже приглашён и ещё не ответил; нового приглашения не создано. */
    ALREADY_PENDING("already_pending"),
    /** Друг уже в звонке; звать некуда. */
    ALREADY_MEMBER("already_member");

    private final String wireValue;

    ConferenceInviteOutcome(String wireValue) {
        this.wireValue = wireValue;
    }

    @JsonValue
    public String wireValue() {
        return wireValue;
    }
}
