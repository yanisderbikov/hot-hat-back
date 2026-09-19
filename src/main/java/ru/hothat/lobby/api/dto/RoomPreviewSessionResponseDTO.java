package ru.hothat.lobby.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Открытая сессия превью: место наблюдателя заведено, видеотокен выдан.
 *
 * <p>Один ответ на то, за чем клиент ходил дважды: сначала он сам заводил себе
 * строку зрителя ({@code setDoc spectators/{uid}} с {@code preview:true},
 * {@code live-preview.js:101}), потом просил токен ({@code POST /api/token} с
 * {@code preview_session}, {@code :104}). Между этими двумя вызовами
 * существовало состояние «зритель есть, токена нет», которое некому было
 * убрать: вкладку закрыли — строка осталась.
 *
 * <p>Идентификатор сессии выдаёт сервер. Раньше его придумывал браузер
 * ({@code crypto.randomUUID}), и он же попадал в имя участника LiveKit: чужой
 * клиент мог назваться сессией другого зрителя и выбить его из комнаты.
 *
 * <p>Имени участника в ответе нет: его знает сам токен, и повторять формулу
 * его сборки в ответе значило бы завести второе место, где она записана.
 */
@Schema(description = "Сессия видеопревью комнаты")
public record RoomPreviewSessionResponseDTO(

        @Schema(description = "Комната, которую смотрим", example = "hat-0f3a9c1d7b2e5480",
                pattern = "^hat-[a-f0-9]{16}$")
        String roomId,

        @Schema(description = "Идентификатор сессии превью: у одного зрителя их может быть несколько "
                + "(две вкладки), и каждая живёт своей жизнью", example = "a1b2c3d4e5f60718")
        String sessionId,

        @Schema(description = "Адрес сервера видеосвязи", example = "wss://livekit.example.com")
        String videoServerUrl,

        @Schema(description = "Токен участника: только на приём, публиковать дорожки им нельзя",
                example = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9")
        String videoToken,

        @Schema(description = "Через сколько миллисекунд подтверждать, что превью ещё открыто",
                example = "60000", type = "integer")
        long heartbeatIntervalMs) {
}
