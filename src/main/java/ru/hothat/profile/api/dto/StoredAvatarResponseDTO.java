package ru.hothat.profile.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Сохранённый аватар.
 *
 * <p>Ответ повторяет содержимое запроса намеренно: старый адрес возвращал
 * ровно это, а клиент подставляет вернувшееся значение в разметку и так
 * убеждается, что показывает сохранённое, а не выбранный в форме файл.
 */
@Schema(description = "Аватар, лежащий в профиле после замены")
public record StoredAvatarResponseDTO(

        @Schema(description = "Сохранённый data-URL аватара",
                example = "data:image/webp;base64,UklGRhIAAABXRUJQ")
        String avatarDataUrl) {
}
