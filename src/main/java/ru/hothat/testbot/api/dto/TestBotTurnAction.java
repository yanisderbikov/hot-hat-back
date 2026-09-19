package ru.hothat.testbot.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Что раннер сделал за этот шаг.
 *
 * <p>Сегодня это свободная строка, которую клиент сравнивает по значению;
 * набор значений нигде не объявлен и восстанавливается только чтением
 * {@code TestBotsServiceImpl}. Здесь он закрыт: Jackson отвергнет чужое
 * значение до входа в контроллер.
 */
@Schema(description = "Исход шага раннера тестовой партии")
public enum TestBotTurnAction {

    /** Партия на паузе — ждём. */
    PAUSED("paused"),
    /** Комната в setup, finished или closed — делать нечего. */
    IDLE("idle"),
    /** Ждём наступления срока: конца апелляции или начала хода. */
    WAITING("waiting"),
    /** Апелляция закрыта, партия поехала дальше. */
    APPEAL_FINALIZED("appeal_finalized"),
    /** Начат новый ход. */
    TURN_STARTED("turn_started"),
    /** Ход закончился. */
    TURN_ENDED("turn_ended"),
    /** Слово угадано. */
    GUESSED("guessed"),
    /** Угадано последнее слово в мешке. */
    GUESSED_LAST("guessed_last"),
    /** Слово пропущено. */
    SKIPPED("skipped"),
    /** Пропущено единственное оставшееся слово. */
    SKIPPED_ONLY_WORD("skipped_only_word"),
    /** Ход ведёт владелец вручную — раннер не вмешивается. */
    OWNER_CONTROLS_WORDS("owner_controls_words"),
    /** Партия завершена. */
    FINISHED("finished");

    private final String wireValue;

    TestBotTurnAction(String wireValue) {
        this.wireValue = wireValue;
    }

    @com.fasterxml.jackson.annotation.JsonValue
    public String wireValue() {
        return wireValue;
    }

    /** Значение из старого движка; неизвестное — {@link #IDLE}, шаг просто пропускается. */
    public static TestBotTurnAction fromWire(String value) {
        for (TestBotTurnAction action : values()) {
            if (action.wireValue.equals(value)) {
                return action;
            }
        }
        return IDLE;
    }
}
