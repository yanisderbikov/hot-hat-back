package ru.hothat.conference.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Человек в списке видео-чата: участник или приглашённый.
 *
 * <p>Общая проекция: одна форма на оба списка. Ник и аватар берутся из
 * карточки игрока на чтении — копий в таблицах видео-чата нет.
 */
@Schema(description = "Игрок в списке видео-чата")
public record ConferencePlayerView(

        @Schema(description = "Идентификатор игрока", example = "Qk3xZaTb9mNpR2sVuWyA1cEfGhJk")
        String uid,

        @Schema(description = "Ник для показа", example = "Vasya")
        String nickname,

        @Schema(description = "Аватар как data-URL; null — аватара нет", nullable = true)
        String avatarDataUrl) {
}
