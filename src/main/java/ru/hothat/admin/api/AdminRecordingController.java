package ru.hothat.admin.api;

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
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.hothat.admin.api.dto.AdminRecordingQueryDTO;
import ru.hothat.admin.api.dto.AdminRecordingUrlsResponseDTO;
import ru.hothat.admin.api.dto.AdminRecordingsPageResponseDTO;
import ru.hothat.admin.usecase.DeleteRecordingByAdminUseCase;
import ru.hothat.admin.usecase.IssueAdminPlaybackUrlsUseCase;
import ru.hothat.admin.usecase.ListRecordingsForAdminUseCase;
import ru.hothat.config.HotHatUser;

/**
 * Админский каталог записей партий.
 *
 * <p>Заменяет три действия {@code POST /api/recordings}: {@code admin_list},
 * {@code admin_urls} и {@code admin_delete}. Все три жили ветками внутри
 * общего {@code case}, а право администратора вычислялось прямо в аргументе
 * вызова ({@code RecordingsController:51}).
 *
 * <p>Старый адрес пока жив: фронтенд переедет на эти маршруты отдельно.
 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v2/admin/recordings")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin · записи", description = "Каталог записей партий")
@SecurityRequirement(name = "Bearer")
public class AdminRecordingController {

    private final ListRecordingsForAdminUseCase listRecordings;
    private final IssueAdminPlaybackUrlsUseCase issueUrls;
    private final DeleteRecordingByAdminUseCase deleteRecording;

    @Operation(summary = "Показать каталог записей",
            description = "Записи всех игроков, свежие сверху. Ничего не удаляет и никого не "
                    + "опрашивает: уборка просроченных — дело планового маршрута, а состояние "
                    + "незавершённой записи приносит вебхук Egress.")
    @GetMapping
    public ResponseEntity<AdminRecordingsPageResponseDTO> list(
            @AuthenticationPrincipal HotHatUser admin,
            @ParameterObject @Valid AdminRecordingQueryDTO query) {
        return ResponseEntity.ok(listRecordings.run(admin, query));
    }

    @Operation(summary = "Получить ссылки на запись",
            description = "Две подписанные ссылки на любую запись — для плеера и для скачивания. "
                    + "Обе живут полчаса.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Ссылки выданы"),
            @ApiResponse(responseCode = "404", description = "RECORDING_NOT_FOUND: такой записи нет",
                    content = @Content),
            @ApiResponse(responseCode = "409", description = "RECORDING_NOT_READY: файла ещё нет",
                    content = @Content)})
    @GetMapping("/{recordingId}/playback-urls")
    public ResponseEntity<AdminRecordingUrlsResponseDTO> urls(
            @AuthenticationPrincipal HotHatUser admin,
            @Parameter(description = "Запись партии", example = "hat-0f3a9c1d7b2e5480-3")
            @PathVariable
            @Pattern(regexp = "^[A-Za-z0-9_.:@+-]{3,180}$", message = "Некорректная запись.")
            String recordingId) {
        return ResponseEntity.ok(issueUrls.run(admin, recordingId));
    }

    @Operation(summary = "Удалить запись",
            description = "Сносит файл и переводит запись в состояние deleted. Строка остаётся: "
                    + "на неё ссылаются сообщения переписки, и снос строки оставил бы в чужом "
                    + "чате карточку, ведущую в никуда.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Записи больше нет"),
            @ApiResponse(responseCode = "404", description = "RECORDING_NOT_FOUND: такой записи нет",
                    content = @Content)})
    @DeleteMapping("/{recordingId}")
    public ResponseEntity<Void> delete(
            @AuthenticationPrincipal HotHatUser admin,
            @Parameter(description = "Запись партии", example = "hat-0f3a9c1d7b2e5480-3")
            @PathVariable
            @Pattern(regexp = "^[A-Za-z0-9_.:@+-]{3,180}$", message = "Некорректная запись.")
            String recordingId) {
        deleteRecording.run(admin, recordingId);
        return ResponseEntity.noContent().build();
    }
}
