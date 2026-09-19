package ru.hothat.conference.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * Видео-чат так, как его видит участник.
 *
 * <p>Общая проекция: та же форма у ответа {@code GET /api/v2/conference/{id}}
 * и у кадра канала {@code /ws/v2/conference/{id}}, поэтому у экрана одна
 * ветка отрисовки. Списки — по состоянию участия: в {@code participants}
 * те, кто принял приглашение или завёл видео-чат, в {@code invited} — кого
 * позвали и кто ещё не ответил. Отклонившие, ушедшие и выгнанные не видны.
 */
@Schema(description = "Видео-чат глазами участника")
public record ConferenceView(

        @Schema(description = "Идентификатор видео-чата", example = "vc-0f3a9c1d7b2e5480")
        String conferenceId,

        @Schema(description = "Кто завёл видео-чат; он же вправе выгонять и заводить комнату",
                example = "Qk3xZaTb9mNpR2sVuWyA1cEfGhJk")
        String hostUid,

        @Schema(description = "Участники: приняли приглашение либо завели видео-чат")
        List<ConferencePlayerView> participants,

        @Schema(description = "Приглашённые, ещё не ответившие")
        List<ConferencePlayerView> invited,

        @Schema(description = "Сколько человек помещается в звонок", example = "16", type = "integer")
        int maxParticipants,

        @Schema(description = "Когда видео-чат истечёт, миллисекунды эпохи", example = "1788643200000",
                type = "integer")
        long expiresAtMs,

        @Schema(description = "Комната, заведённая этим составом; null — не заводили", nullable = true)
        ConferenceGameRoomView gameRoom) {
}
