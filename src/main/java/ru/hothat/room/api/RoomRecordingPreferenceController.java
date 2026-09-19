package ru.hothat.room.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.hothat.common.validation.RoomId;
import ru.hothat.config.HotHatUser;
import ru.hothat.room.api.dto.RecordingPreferenceResponseDTO;
import ru.hothat.room.api.dto.SetRecordingPreferenceRequestDTO;
import ru.hothat.room.usecase.SetRecordingPreferenceUseCase;

/**
 * Записывать ли партии этой комнаты.
 *
 * <p>Заменяет {@code set_recording_preference} ({@code app-core.js:12737}).
 *
 * <p>Отдельный класс, потому что уровень прав здесь свой и единственный во всей
 * области: владелец сервиса. Запись стоит денег у внешнего сервиса, и решать за
 * его счёт не может ни хозяин комнаты, ни администратор. Сегодня это проверка
 * по адресу почты внутри сервиса — одна из семи точек, где правило владельца
 * пересчитывается заново (находка F7); здесь роль проверяется ролью, и до сих
 * пор не проверявшийся нигде {@code OWNER} получает своего второго потребителя.
 *
 * <p>Старый путь пока жив: фронтенд переедет на этот маршрут отдельно.
 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v2/room/{roomId}/recording-preference")
@PreAuthorize("hasRole('OWNER')")
@Tag(name = "Room · запись партий", description = "Включение записи партий в комнате")
@SecurityRequirement(name = "Bearer")
public class RoomRecordingPreferenceController {

    private final SetRecordingPreferenceUseCase setRecordingPreference;

    @Operation(summary = "Включить или выключить запись партий",
            description = "Только до старта партии: начатую игру дописать с середины нельзя, а "
                    + "выключить запись на ходу значило бы оборвать уже пишущийся файл. Владелец "
                    + "должен быть в комнате — иначе он включал бы запись чужой партии по одному "
                    + "идентификатору из чата.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Настройка записана"),
            @ApiResponse(responseCode = "403", description = "ROOM_MEMBER_ONLY: вы не в этой комнате",
                    content = @Content),
            @ApiResponse(responseCode = "404", description = "ROOM_NOT_FOUND: комнаты нет",
                    content = @Content),
            @ApiResponse(responseCode = "409", description = "GAME_ALREADY_STARTED: партия уже идёт",
                    content = @Content)})
    @PutMapping
    public ResponseEntity<RecordingPreferenceResponseDTO> set(
            @AuthenticationPrincipal HotHatUser user,
            @Parameter(description = "Игровая комната", example = "hat-0f3a9c1d7b2e5480")
            @PathVariable @RoomId String roomId,
            @Valid @RequestBody SetRecordingPreferenceRequestDTO request) {
        return ResponseEntity.ok(setRecordingPreference.run(user, roomId, request));
    }
}
