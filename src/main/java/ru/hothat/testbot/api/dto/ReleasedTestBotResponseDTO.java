package ru.hothat.testbot.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** The freed bot UID remains a reservation until a human claims that team slot. */
@Schema(description = "Освобождённый слот тест-бота")
public record ReleasedTestBotResponseDTO(
        String roomId, String botUid, String teamId) {
}
