package ru.hothat.lobby.api.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Размер комнаты: сколько игроков собираем.
 *
 * <p>Набор закрыт четырьмя значениями, потому что игроки садятся парами и
 * команд бывает от двух до пяти. Сегодня число приходит любым, а
 * {@code MatchmakingServiceImpl.normalizeSize} тихо подменяет неподходящее на
 * десять: попросил комнату на шестерых, опечатался в семёрке — ищешь
 * десятерых и не понимаешь, почему так долго.
 *
 * <p>Перечисление, а не {@code @Min/@Max}: «от четырёх до десяти» пропустило
 * бы пятёрку, при которой одна команда остаётся без пары.
 */
@Schema(description = "Сколько игроков собирает комната", type = "integer",
        allowableValues = {"4", "6", "8", "10"}, example = "10")
public enum MatchSize {

    FOUR(4),
    SIX(6),
    EIGHT(8),
    TEN(10);

    /** Ранговая комната всегда десятиместная: клиент размера и не спрашивает. */
    public static final MatchSize RANKED = TEN;

    private final int players;

    MatchSize(int players) {
        this.players = players;
    }

    @JsonValue
    public int players() {
        return players;
    }

    /**
     * Значение из тела запроса. Незнакомое отвергаем исключением: Jackson
     * превратит его в 400 с перечислением допустимых, и это ровно то, чего
     * не хватало старому молчаливому округлению до десяти.
     */
    @JsonCreator
    public static MatchSize fromWire(int value) {
        for (MatchSize size : values()) {
            if (size.players == value) {
                return size;
            }
        }
        throw new IllegalArgumentException("Комната бывает на 4, 6, 8 или 10 игроков.");
    }
}
