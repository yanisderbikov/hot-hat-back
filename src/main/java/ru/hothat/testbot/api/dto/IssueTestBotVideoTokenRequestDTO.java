package ru.hothat.testbot.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * Заявка на видеотокен тестового бота.
 *
 * <p>Бот — не браузер: попросить токен за себя он не может, и его просит
 * владелец тестовой комнаты ({@code test-mode.js:286}). Отсюда единственное
 * поле — за кого просят.
 *
 * <p>Оно обязательное, и это принципиально. Сегодня то же самое делает
 * {@code POST /api/token} с необязательным {@code participant_identity}:
 * пустое значение молча превращает запрос «токен боту» в «токен себе», и
 * опечатка в идентификаторе бота уводила бы владельца в комнату второй
 * личностью. Здесь пустое значение — отказ разбора, а не другая операция.
 *
 * <p>Ни комнаты, ни роли, ни имени участника в теле нет: комната стоит в
 * адресе, роль задаёт сам адрес, имя сервер берёт из места бота за столом.
 */
@Schema(description = "Заявка на видеотокен тестового бота")
public record IssueTestBotVideoTokenRequestDTO(

        @Schema(description = "Бот, за которого берут токен. Только бот этой тестовой комнаты: "
                + "имена ботов сервер раздаёт сам при подъёме отряда",
                example = "testbot-7b2e5480-1", pattern = "^testbot-[a-z0-9-]{3,80}$")
        @NotBlank(message = "Нужно назвать бота.")
        @Pattern(regexp = "^testbot-[a-z0-9-]{3,80}$", message = "Некорректный участник видеосвязи.")
        String botIdentity) {
}
