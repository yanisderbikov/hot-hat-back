package ru.hothat.game.api.dto;

/** True only after the authenticated target acknowledged the outgoing playback. */
public record ConfirmedReplacementPlaybackResponseDTO(boolean charged, boolean clipConsumed) {
}
