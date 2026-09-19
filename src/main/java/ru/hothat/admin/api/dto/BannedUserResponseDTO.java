package ru.hothat.admin.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Заведённая блокировка.
 *
 * <p>Прежний ответ был {@code {ok:true}} и не говорил ничего: ни кто автор, ни
 * что стало с местом игрока в комнате. Автор теперь сохраняется и возвращается
 * — блокировка это решение человека, и оно должно быть подписано.
 *
 * <p>Номера поколения токенов здесь нет намеренно. Это внутренний счётчик
 * подсистемы авторизации: администратору он не говорит ничего, что можно
 * сделать, а наружу выносит устройство проверки токенов. Консоли нужен ответ
 * на вопрос «выкинуло ли его прямо сейчас», и он назван словом.
 */
@Schema(description = "Заведённая блокировка игрока")
public record BannedUserResponseDTO(

        @Schema(description = "Кто заблокирован", example = "Qk3xZaTb9mNpR2sVuWyA1cEfGhJk")
        String uid,

        @Schema(description = "Причина, сохранённая в истории", example = "Оскорбления в чате")
        String reason,

        @Schema(description = "Кто заблокировал", example = "Ab1cDeFgHiJkLmNoPqRsTuVwXyZ0")
        String bannedByUid,

        @Schema(description = "Когда заблокировали", example = "1788600000000", type = "integer")
        long bannedAtMs,

        @Schema(description = "Место игрока в названной комнате помечено как забаненное; "
                + "false — комнату не называли или места в ней нет", example = "true", type = "boolean")
        boolean roomSeatRevoked,

        @Schema(description = "Живые сессии игрока оборваны: и refresh-токены, и уже выданные "
                + "access-токены. Всегда true — блокировка без этого не блокировка",
                example = "true", type = "boolean")
        boolean sessionsRevoked) {
}
