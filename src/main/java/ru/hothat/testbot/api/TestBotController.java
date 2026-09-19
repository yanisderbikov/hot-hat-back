package ru.hothat.testbot.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.hothat.common.validation.RoomId;
import ru.hothat.config.HotHatUser;
import ru.hothat.testbot.api.dto.FireOwnerFartRequestDTO;
import ru.hothat.testbot.api.dto.FiredOwnerFartResponseDTO;
import ru.hothat.testbot.api.dto.IssueTestBotVideoTokenRequestDTO;
import ru.hothat.testbot.api.dto.SynthesizeSpeechRequestDTO;
import ru.hothat.testbot.api.dto.SynthesizedSpeechResponseDTO;
import ru.hothat.testbot.api.dto.TestBotSquadResponseDTO;
import ru.hothat.testbot.api.dto.TestBotTurnResponseDTO;
import ru.hothat.testbot.api.dto.TestBotVideoTokenResponseDTO;
import ru.hothat.testbot.usecase.AdvanceTestBotTurnUseCase;
import ru.hothat.testbot.usecase.FireOwnerFartUseCase;
import ru.hothat.testbot.usecase.IssueTestBotVideoTokenUseCase;
import ru.hothat.testbot.usecase.SetUpTestBotsUseCase;
import ru.hothat.testbot.usecase.StopTestBotsUseCase;
import ru.hothat.testbot.usecase.SynthesizeSpeechUseCase;

/**
 * Отряд тестовых ботов комнаты.
 *
 * <p>Заменяет {@code POST /api/test-bots}, где одно тело с полем {@code action}
 * выбирало между четырьмя операциями и возвращало четыре несовместимых объекта
 * под одной схемой {@code Map<String,Object>}. Здесь у каждой операции свой
 * адрес, своя пара DTO и своя запись в спецификации.
 *
 * <p>Сюда же собраны два действия, живших вне {@code /api/test-bots} и потому
 * защищённых одной лишь ролью {@code ADMIN}: видеотокен бота
 * ({@code POST /api/token} с чужим {@code participant_identity}) и озвучка
 * «облака мыслей» ({@code POST /api/tts}). Оба достижимы только в своей
 * тестовой комнате — раньше это знал лишь интерфейс, теперь знает сервер.
 *
 * <p>Уровень прав у класса один: администратор в своей тестовой комнате.
 * Роль стоит на каждом use-case, владение комнатой — предусловие сценария.
 *
 * <p>Старый адрес пока жив: фронтенд переедет на эти маршруты отдельно.
 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v2/admin/test-rooms/{roomId}/bots")
@Tag(name = "Admin · тестовые боты", description = "Отряд ботов в тестовой комнате")
@SecurityRequirement(name = "Bearer")
public class TestBotController {

    private final SetUpTestBotsUseCase setUpTestBots;
    private final StopTestBotsUseCase stopTestBots;
    private final AdvanceTestBotTurnUseCase advanceTurn;
    private final FireOwnerFartUseCase fireOwnerFart;
    private final IssueTestBotVideoTokenUseCase issueVideoToken;
    private final SynthesizeSpeechUseCase synthesizeSpeech;

    @Operation(summary = "Поднять отряд ботов",
            description = "Рассаживает ботов по командам и готовит комнату к прогону. "
                    + "Требует включённой возможности bot_enabled и владения тестовой комнатой.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Отряд поднят"),
            @ApiResponse(responseCode = "403", description = "Возможность выключена или комната не тестовая",
                    content = @Content)})
    @PostMapping
    public ResponseEntity<TestBotSquadResponseDTO> setUp(
            @AuthenticationPrincipal HotHatUser admin,
            @Parameter(description = "Тестовая комната, где живёт отряд", example = "hat-0f3a9c1d7b2e5480")
            @PathVariable @RoomId String roomId) {
        return ResponseEntity.status(HttpStatus.CREATED).body(setUpTestBots.run(admin, roomId));
    }

    @Operation(summary = "Распустить отряд и закрыть комнату")
    @ApiResponse(responseCode = "204", description = "Комната закрыта")
    @DeleteMapping
    public ResponseEntity<Void> stop(
            @AuthenticationPrincipal HotHatUser admin,
            @Parameter(description = "Тестовая комната, где живёт отряд", example = "hat-0f3a9c1d7b2e5480")
            @PathVariable @RoomId String roomId) {
        stopTestBots.run(admin, roomId);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Сделать шаг раннера",
            description = "Продвигает тестовую партию на один шаг и говорит, "
                    + "через сколько миллисекунд разбудить раннер снова.")
    @PostMapping("/turn")
    public ResponseEntity<TestBotTurnResponseDTO> advance(
            @AuthenticationPrincipal HotHatUser admin,
            @Parameter(description = "Тестовая комната, где живёт отряд", example = "hat-0f3a9c1d7b2e5480")
            @PathVariable @RoomId String roomId) {
        return ResponseEntity.ok(advanceTurn.run(admin, roomId));
    }

    @Operation(summary = "Выпустить диверсию владельца",
            description = "Событие без боезапаса и кулдауна. Идемпотентно по eventId: "
                    + "повтор с тем же значением не проигрывает звук дважды.")
    @ApiResponse(responseCode = "201", description = "Событие создано и разослано")
    @PostMapping("/owner-farts")
    public ResponseEntity<FiredOwnerFartResponseDTO> fireFart(
            @AuthenticationPrincipal HotHatUser admin,
            @Parameter(description = "Тестовая комната, где живёт отряд", example = "hat-0f3a9c1d7b2e5480")
            @PathVariable @RoomId String roomId,
            @Valid @RequestBody(required = false) FireOwnerFartRequestDTO request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(fireOwnerFart.run(admin, roomId, request));
    }

    @Operation(summary = "Выдать видеотокен боту",
            description = "Единственный адрес, где токен берут за другого участника. "
                    + "Бот не браузер и попросить за себя не может, поэтому за него просит "
                    + "владелец комнаты. Личность обязана быть ботом, уже сидящим за столом "
                    + "именно этой тестовой комнаты.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Токен выдан"),
            @ApiResponse(responseCode = "403", description = "TEST_BOT_NOT_IN_ROOM: это не бот вашей "
                    + "тестовой комнаты; TOKEN_IDENTITY_FORBIDDEN: имя не похоже на бота; "
                    + "FEATURE_DISABLED: тест-режим выключен", content = @Content),
            @ApiResponse(responseCode = "404", description = "ROOM_NOT_FOUND: комнаты нет", content = @Content),
            @ApiResponse(responseCode = "410", description = "ROOM_CLOSED_BY_ADMIN: комната закрыта",
                    content = @Content)})
    @PostMapping("/video-tokens")
    public ResponseEntity<TestBotVideoTokenResponseDTO> issueVideoToken(
            @AuthenticationPrincipal HotHatUser admin,
            @Parameter(description = "Тестовая комната, где живёт отряд", example = "hat-0f3a9c1d7b2e5480")
            @PathVariable @RoomId String roomId,
            @Valid @RequestBody IssueTestBotVideoTokenRequestDTO request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(issueVideoToken.run(admin, roomId, request));
    }

    @Operation(summary = "Озвучить реплику бота",
            description = "Синтезирует короткую фразу «облака мыслей» и отдаёт звук строкой base64. "
                    + "Отвечает 200, а не 201: на сервере ничего не появляется — одинаковые реплики "
                    + "отдаются из кеша, и адреса у результата нет.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Реплика озвучена"),
            @ApiResponse(responseCode = "403", description = "TEST_ROOM_ONLY: это не ваша тестовая комната; "
                    + "FEATURE_DISABLED: тест-режим выключен", content = @Content),
            @ApiResponse(responseCode = "404", description = "ROOM_NOT_FOUND: комнаты нет", content = @Content),
            @ApiResponse(responseCode = "502", description = "TTS_UNAVAILABLE или TTS_AUDIO_INVALID: "
                    + "синтезатор речи не ответил или прислал негодный звук", content = @Content)})
    @PostMapping("/speech-syntheses")
    public ResponseEntity<SynthesizedSpeechResponseDTO> synthesizeSpeech(
            @AuthenticationPrincipal HotHatUser admin,
            @Parameter(description = "Тестовая комната, где живёт отряд", example = "hat-0f3a9c1d7b2e5480")
            @PathVariable @RoomId String roomId,
            @Valid @RequestBody SynthesizeSpeechRequestDTO request) {
        return ResponseEntity.ok(synthesizeSpeech.run(admin, roomId, request));
    }
}
