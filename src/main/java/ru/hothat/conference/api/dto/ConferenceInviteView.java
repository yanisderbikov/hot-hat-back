package ru.hothat.conference.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Своё ждущее приглашение в видео-чат.
 *
 * <p>Общая проекция: та же форма у {@code GET /api/v2/conference/invites}
 * и у поля {@code conferenceInvites} кадра {@code /ws/v2/me/social}, откуда
 * карточка «Вступить / Отклонить» появляется на любой странице портала.
 */
@Schema(description = "Приглашение в видео-чат, ждущее ответа")
public record ConferenceInviteView(

        @Schema(description = "Куда зовут", example = "vc-0f3a9c1d7b2e5480")
        String conferenceId,

        @Schema(description = "Кто позвал", example = "Qk3xZaTb9mNpR2sVuWyA1cEfGhJk")
        String inviterUid,

        @Schema(description = "Ник позвавшего", example = "Vasya")
        String inviterNickname,

        @Schema(description = "Аватар позвавшего как data-URL; null — аватара нет", nullable = true)
        String inviterAvatarDataUrl,

        @Schema(description = "Когда позвали, миллисекунды эпохи", example = "1788600000000", type = "integer")
        long createdAtMs,

        @Schema(description = "Когда видео-чат истечёт вместе с приглашением, миллисекунды эпохи",
                example = "1788643200000", type = "integer")
        long expiresAtMs) {
}
