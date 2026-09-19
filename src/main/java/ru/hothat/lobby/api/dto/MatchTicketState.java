package ru.hothat.lobby.api.dto;

import com.fasterxml.jackson.annotation.JsonValue;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Что сейчас с заявкой на подбор.
 *
 * <p>Сегодня один и тот же вызов {@code matchmake} отдаёт два несовместимых
 * объекта: одиннадцать ключей при удаче и пять при провале, а клиент
 * различает их по наличию поля {@code failed}
 * ({@code MatchmakingServiceImpl:177-183} против {@code :200-212}). Здесь
 * состояние названо полем, форма ответа одна.
 */
@Schema(description = "Состояние заявки на подбор")
public enum MatchTicketState {

    /** Комната ещё набирается: продолжайте опрашивать заявку. */
    SEARCHING("searching"),
    /** Состав собран — можно входить в комнату. */
    MATCHED("matched"),
    /** Срок поиска вышел, комната распущена. Заявку нужно подавать заново. */
    EXPIRED("expired");

    private final String wireValue;

    MatchTicketState(String wireValue) {
        this.wireValue = wireValue;
    }

    @JsonValue
    public String wireValue() {
        return wireValue;
    }
}
