package ru.hothat.machine.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Итог планового удаления протухших токенов.
 *
 * <p>Уборка была написана и ни разу не вызывалась: {@code SaverUser.deleteExpiredTokens}
 * реализован в {@code UserManager:164-172}, но во всём проекте у него нет ни
 * одного вызывающего. Таблицы {@code refresh_token} и {@code password_reset_token}
 * росли без предела.
 *
 * <p>Счётчика удалённых строк здесь нет намеренно: хранилище объявляет метод
 * как {@code void}, а менять чужой репозиторий из машинной области нельзя.
 * Пустое поле-счётчик соврало бы, поэтому его нет вовсе.
 */
@Schema(description = "Итог уборки протухших токенов")
public record TokenSweepResponseDTO(

        @Schema(description = "Токены со сроком раньше этого момента удалены, ISO-8601",
                example = "2026-09-06T02:40:00Z")
        String expiredBefore) {
}
