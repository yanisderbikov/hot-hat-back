package ru.hothat.rating.api.dto;

import com.fasterxml.jackson.annotation.JsonValue;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Чем кончился зачёт партии.
 *
 * <p>Сегодня этот же вызов отвечает тремя несовместимыми объектами:
 * {@code {recorded:false}}, {@code {recorded:true, annulled:true, …}} и полным
 * {@code {recorded:true, technical, culpritTeamIds, …}}. Клиент вынужден
 * различать их по наличию ключей. Здесь исход назван полем, а необязательное
 * выражено пустыми значениями.
 */
@Schema(description = "Исход зачёта результата партии")
public enum MatchResultOutcome {

    /** Очки начислены, таблица сезона обновлена. */
    RECORDED("recorded"),

    /**
     * Партия аннулирована: связь оборвалась у стольких игроков, что винить
     * некого. Ни очков, ни технических поражений.
     */
    ANNULLED("annulled"),

    /**
     * Эту партию уже зачли раньше. Не ошибка: клиент шлёт зачёт из обработчика
     * конца партии, и обработчик срабатывает у каждого участника.
     */
    ALREADY_RECORDED("already_recorded");

    private final String wireValue;

    MatchResultOutcome(String wireValue) {
        this.wireValue = wireValue;
    }

    @JsonValue
    public String wireValue() {
        return wireValue;
    }
}
