package ru.hothat.friend.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Присутствие одного друга.
 *
 * <p>Отдельно от карточки друга, потому что живёт по-другому: карточка
 * меняется раз в месяц, присутствие — каждую минуту, и опрашивают их
 * с разной частотой.
 */
@Schema(description = "Присутствие друга")
public record FriendPresenceView(

        @Schema(description = "Идентификатор игрока", example = "Qk3xZaTb9mNpR2sVuWyA1cEfGhJk",
                pattern = "^[A-Za-z0-9]{28}$")
        String uid,

        @Schema(description = "Когда игрока видели в последний раз, миллисекунды эпохи; 0 — не видели ни разу",
                example = "1788600000000", type = "integer")
        long lastSeenAtMs,

        @Schema(description = "Считается ли игрок сейчас в сети", example = "true", type = "boolean")
        boolean online) {
}
