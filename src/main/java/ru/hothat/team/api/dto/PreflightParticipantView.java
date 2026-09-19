package ru.hothat.team.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Один участник проверки готовности.
 *
 * <p>Общая проекция: включается полем во все три ответа, где виден снимок
 * префлайта.
 *
 * <p>Сводит три параллельные карты старого ответа — {@code media},
 * {@code ready} и {@code memberNicknames} — в одну строку на человека. Клиент
 * читал их по одному и тому же ключу трижды и рисовал строку из трёх обращений
 * ({@code preflight?.ready?.[user.uid]}); при расхождении ключей строка
 * получалась про разных людей.
 */
@Schema(description = "Участник проверки готовности")
public record PreflightParticipantView(

        @Schema(description = "Идентификатор участника", example = "Qk3xZaTb9mNpR2sVuWyA1cEfGhJk")
        String uid,

        @Schema(description = "Ник участника", example = "vasya")
        String nickname,

        @Schema(description = "Камера и микрофон отвечают. Признак протухает за 25 секунд без "
                + "подтверждения: считает его сервер, потому что у клиента часы могут уехать",
                example = "true", type = "boolean")
        boolean mediaOk,

        @Schema(description = "Нажал «готов». Всегда false, пока не подтверждена связь: "
                + "играть вслепую нельзя", example = "true", type = "boolean")
        boolean ready) {
}
