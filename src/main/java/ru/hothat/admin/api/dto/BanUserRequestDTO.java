package ru.hothat.admin.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import ru.hothat.common.validation.PlayerUid;
import ru.hothat.common.validation.RoomId;

/**
 * Кого заблокировать и за что.
 *
 * <p>Комната необязательна: бан существует сам по себе, а комната лишь
 * говорит, откуда игрока заодно выставить. Раньше оба поля приезжали в общем
 * теле действия и не проверялись ничем — {@code room_id} просто приводился к
 * нижнему регистру ({@code AdminController:84}).
 */
@Schema(description = "Запрос на блокировку игрока")
public record BanUserRequestDTO(

        @Schema(description = "Кого блокируем", example = "Qk3xZaTb9mNpR2sVuWyA1cEfGhJk")
        @NotBlank(message = "Не указан игрок.")
        @PlayerUid
        String uid,

        /**
         * Комната, из которой игрока надо вышвырнуть заодно. Пустая строка
         * сюда не годится: «не указана комната» — это отсутствие поля.
         */
        @Schema(description = "Комната, из которой заодно выставить игрока; можно не задавать",
                example = "hat-0f3a9c1d7b2e5480", pattern = "^hat-[a-f0-9]{16}$", nullable = true)
        @RoomId
        String roomId,

        @Schema(description = "Причина: её видит следующий администратор в истории",
                example = "Оскорбления в чате", nullable = true)
        @Size(max = 300, message = "Причина длиннее 300 символов не сохраняется.")
        String reason) {
}
