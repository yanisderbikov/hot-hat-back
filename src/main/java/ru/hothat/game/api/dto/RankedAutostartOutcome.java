package ru.hothat.game.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Чем кончилась попытка автозапуска рейтинговой партии.
 *
 * <p>Раньше на этот вопрос приходило три несовместимых ответа, и клиент
 * различал их по наличию ключей {@code waiting} и {@code phase} (замечание C6
 * аудита). Теперь исход назван словом.
 */
@Schema(description = "Исход автозапуска")
public enum RankedAutostartOutcome {

    /** Партия запущена этим вызовом. */
    STARTED,
    /** Собрались не все: ждём остальных. */
    WAITING,
    /** Партия уже идёт — её запустил кто-то другой тем же вызовом. */
    ALREADY_RUNNING
}
