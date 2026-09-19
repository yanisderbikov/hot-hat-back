package ru.hothat.recording.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.hothat.config.HotHatUser;
import ru.hothat.recording.api.dto.MyRecordingQueryDTO;
import ru.hothat.recording.api.dto.MyRecordingsPageResponseDTO;
import ru.hothat.recording.api.dto.SavedRecordingResponseDTO;
import ru.hothat.recording.usecase.ForgetRecordingUseCase;
import ru.hothat.recording.usecase.ListMyRecordingsUseCase;
import ru.hothat.recording.usecase.SaveRecordingUseCase;

/**
 * Личная библиотека записей игрока.
 *
 * <p>Заменяет три действия {@code POST /api/recordings} — {@code list_mine}
 * ({@code portal.js:55}), {@code save} ({@code app-core.js:14731}) и
 * {@code remove_saved} ({@code portal.js:68}), — которые ходили одним адресом
 * с полем {@code action} в теле и возвращали три разных объекта под одной
 * схемой {@code Map<String,Object>}.
 *
 * <p>Библиотека — ресурс: список читается, запись в него кладётся под своим
 * идентификатором ({@code PUT}, потому что повторное сохранение даёт тот же
 * итог) и убирается ({@code DELETE}). «Убрать из библиотеки» не значит
 * «удалить файл»: удаление записи целиком — админский адрес со своим уровнем
 * прав.
 *
 * <p>Уровень прав у класса один: свою библиотеку ведёт вошедший игрок.
 * Кто вправе сохранить запись — участник записанной партии — это предусловие
 * сценария, а не уровень доступа к адресу.
 *
 * <p>Старый адрес пока жив: фронтенд переедет на эти маршруты отдельно.
 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v2/recording/mine")
@Tag(name = "Recording · библиотека", description = "Личная библиотека записей игрока")
@SecurityRequirement(name = "Bearer")
public class MyRecordingLibraryController {

    private final ListMyRecordingsUseCase listMyRecordings;
    private final SaveRecordingUseCase saveRecording;
    private final ForgetRecordingUseCase forgetRecording;

    @Operation(summary = "Показать мои записи",
            description = "Записи от свежих к старым. У тех, что ещё снимаются или собираются, "
                    + "сервер по дороге подтягивает свежий статус из LiveKit и хранилища.")
    @GetMapping
    public ResponseEntity<MyRecordingsPageResponseDTO> list(
            @AuthenticationPrincipal HotHatUser user,
            @Valid @ParameterObject MyRecordingQueryDTO query) {
        return ResponseEntity.ok(listMyRecordings.run(user, query));
    }

    @Operation(summary = "Сохранить запись в библиотеку",
            description = "Снимает с записи срок хранения: пока она лежит хотя бы у одного игрока, "
                    + "уборщик её не тронет. Съёмку не останавливает — сохранить можно и идущую партию. "
                    + "Повторное сохранение даёт тот же ответ.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Запись в вашей библиотеке"),
            @ApiResponse(responseCode = "403", description = "RECORDING_PARTICIPANT_ONLY: сохранить запись "
                    + "может только участник той партии", content = @Content),
            @ApiResponse(responseCode = "404", description = "RECORDING_NOT_FOUND: такой записи нет",
                    content = @Content),
            @ApiResponse(responseCode = "409", description = "RECORDING_NOT_AVAILABLE: запись не получилась "
                    + "или уже удалена", content = @Content)})
    @PutMapping("/{recordingId}")
    public ResponseEntity<SavedRecordingResponseDTO> save(
            @AuthenticationPrincipal HotHatUser user,
            @Parameter(description = "Запись партии", example = "hat-0f3a9c1d7b2e5480-3")
            @PathVariable
            @Pattern(regexp = "^[A-Za-z0-9_.:@+-]{3,180}$", message = "Некорректная запись.")
            String recordingId) {
        return ResponseEntity.ok(saveRecording.run(user, recordingId));
    }

    @Operation(summary = "Убрать запись из библиотеки",
            description = "Файл не удаляется — уходит только пометка владения. Если запись забрал "
                    + "последний, ей возвращается обычный срок хранения. Записи, которой у вас не было, "
                    + "удаление не меняет ничего.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Записи в вашей библиотеке больше нет"),
            @ApiResponse(responseCode = "404", description = "RECORDING_NOT_FOUND: такой записи нет",
                    content = @Content)})
    @DeleteMapping("/{recordingId}")
    public ResponseEntity<Void> forget(
            @AuthenticationPrincipal HotHatUser user,
            @Parameter(description = "Запись партии", example = "hat-0f3a9c1d7b2e5480-3")
            @PathVariable
            @Pattern(regexp = "^[A-Za-z0-9_.:@+-]{3,180}$", message = "Некорректная запись.")
            String recordingId) {
        forgetRecording.run(user, recordingId);
        return ResponseEntity.noContent().build();
    }
}
