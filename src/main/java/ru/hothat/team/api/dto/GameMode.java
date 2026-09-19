package ru.hothat.team.api.dto;

import com.fasterxml.jackson.annotation.JsonValue;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Режим партии: обычная игра или игра с диверсиями.
 *
 * <p>Сегодня это свободная строка, которую {@code Seasons.mode} молча подменяет
 * на {@code classic} при любом непонятном значении: команда просила рейтинговую
 * игру с диверсиями, опечаталась в режиме и уходила искать соперника в другую
 * лигу без единого сообщения. Здесь набор закрыт — Jackson отвергает чужое
 * значение до входа в контроллер.
 *
 * <p>Список ровно тот, что лежит в {@code ru.hothat.util.Seasons#MODES}.
 * Перечисление объявлено в области команды, потому что первой на v2 переехала
 * она; когда переедут лобби и партия, ему место в общем пакете DTO — там оно
 * понадобится трём областям сразу.
 */
@Schema(description = "Режим партии")
public enum GameMode {

    CLASSIC("classic"),
    SABOTAGE("sabotage");

    private final String wireValue;

    GameMode(String wireValue) {
        this.wireValue = wireValue;
    }

    @JsonValue
    public String wireValue() {
        return wireValue;
    }

    /**
     * Значение из базы или из старого движка. Оно уже прошло
     * {@code Seasons.mode}, поэтому неизвестного здесь быть не должно; если всё
     * же встретилось — тот же запасной {@link #CLASSIC}, что и у нормализатора,
     * иначе чтение префлайта падало бы из-за одной кривой строки в таблице.
     */
    public static GameMode fromWire(String value) {
        for (GameMode mode : values()) {
            if (mode.wireValue.equals(value)) {
                return mode;
            }
        }
        return CLASSIC;
    }
}
