package ru.hothat.game.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Чем кончилась сверка присутствия.
 *
 * <p>Раньше на её месте было пять несовместимых ответов, различавшихся набором
 * ключей: {@code paused}, {@code resumed}, {@code technicalFinished},
 * {@code deleted}, {@code presenceUnavailable}. Клиент разбирал их наличием
 * поля — отсюда и требование плана свести всё к одному слову.
 */
@Schema(description = "Исход сверки присутствия")
public enum MatchPresenceOutcome {

    /** Все на месте, партия идёт. */
    RUNNING,
    /** Кого-то не хватает: партия остановлена. */
    PAUSED,
    /** Все вернулись: партия продолжена. */
    RESUMED,
    /** Ожидание кончилось: засчитано техническое завершение. */
    TECHNICALLY_FINISHED,
    /** Спросить сервер видеосвязи не удалось: состояние оставлено как было. */
    PRESENCE_UNAVAILABLE,
    /** Партия уже кончилась либо ещё не началась: сверять нечего. */
    NOT_LIVE
}
