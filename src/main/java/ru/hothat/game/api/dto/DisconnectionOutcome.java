package ru.hothat.game.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** Чем кончился доклад о разрыве связи. */
@Schema(description = "Исход разрыва связи")
public enum DisconnectionOutcome {

    /** Партия остановлена и ждёт возвращения. */
    PAUSED,
    /** Ожидание кончилось: засчитано техническое завершение. */
    TECHNICALLY_FINISHED,
    /** Разошлись все: комната закрыта, доигрывать некому. */
    ROOM_CLOSED,
    /** Связь на месте, партия идёт дальше. */
    RUNNING
}
