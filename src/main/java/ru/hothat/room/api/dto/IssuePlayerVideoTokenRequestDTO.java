package ru.hothat.room.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Pattern;

/**
 * Заявка на видеотокен игрока.
 *
 * <p>Ни комнаты, ни роли, ни имени участника в теле нет: комната — в адресе,
 * роль задаёт сам адрес, имя сервер берёт из места игрока. Сегодня всё это
 * присылает клиент ({@code livekit.js:541}) вместе с полем {@code room_name},
 * которое сервер не читает вовсе, — из-за него нельзя включить строгий разбор
 * тела (§10.2 плана).
 *
 * <p>Единственное поле — делегирование за тестового бота: администратор своей
 * тестовой комнаты берёт токен на бота, потому что бот не браузер и попросить
 * за себя не может.
 */
@Schema(description = "Заявка на видеотокен игрока")
public record IssuePlayerVideoTokenRequestDTO(

        @Schema(description = "Участник, за которого берут токен: только тестовый бот своей "
                + "тестовой комнаты. Пусто — токен для себя",
                example = "testbot-vasya-1", pattern = "^testbot-[a-z0-9-]{3,80}$", nullable = true)
        @Pattern(regexp = "^testbot-[a-z0-9-]{3,80}$", message = "Некорректный участник видеосвязи.")
        String participantIdentity) {
}
