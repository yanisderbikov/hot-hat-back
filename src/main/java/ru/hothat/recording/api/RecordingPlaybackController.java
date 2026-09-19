package ru.hothat.recording.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.hothat.config.HotHatUser;
import ru.hothat.recording.api.dto.RecordingPlaybackUrlsResponseDTO;
import ru.hothat.recording.usecase.IssuePlaybackUrlsUseCase;

/**
 * Ссылки на просмотр записи.
 *
 * <p>Заменяет {@code POST /api/recordings} с {@code action=urls}.
 *
 * <p>Отдельный класс с одним адресом — это не дробность ради дробности:
 * у выдачи ссылок свой уровень прав, не совпадающий ни с библиотекой, ни с
 * админским каталогом. Смотреть может тот, кто сохранил запись у себя, и тот,
 * с кем ею поделились в переписке; участник партии, не забравший запись, —
 * не может. Свести это в один класс с библиотекой значило бы объявить два
 * разных права одним.
 *
 * <p>Ссылки подписаны хранилищем и живут полчаса, поэтому адрес выдаёт
 * доступ, а не читает свойство записи; кешировать ответ нельзя.
 *
 * <p>Старый адрес пока жив: фронтенд переедет на этот маршрут отдельно.
 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v2/recording/{recordingId}/playback-urls")
@Tag(name = "Recording · просмотр", description = "Ссылки на просмотр и скачивание записи")
@SecurityRequirement(name = "Bearer")
public class RecordingPlaybackController {

    private final IssuePlaybackUrlsUseCase issuePlaybackUrls;

    @Operation(summary = "Получить ссылки на запись",
            description = "Две подписанные ссылки: одна для плеера, другая для скачивания файлом. "
                    + "Обе живут полчаса — время окончания сервер называет сам.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Ссылки выданы"),
            @ApiResponse(responseCode = "403", description = "RECORDING_NOT_SAVED: запись не сохранена вами "
                    + "и ею с вами не делились", content = @Content),
            @ApiResponse(responseCode = "404", description = "RECORDING_NOT_FOUND: такой записи нет",
                    content = @Content),
            @ApiResponse(responseCode = "409", description = "RECORDING_NOT_READY: файла ещё нет — "
                    + "запись снимается или собирается", content = @Content)})
    @GetMapping
    public ResponseEntity<RecordingPlaybackUrlsResponseDTO> urls(
            @AuthenticationPrincipal HotHatUser user,
            @Parameter(description = "Запись партии", example = "hat-0f3a9c1d7b2e5480-3")
            @PathVariable
            @Pattern(regexp = "^[A-Za-z0-9_.:@+-]{3,180}$", message = "Некорректная запись.")
            String recordingId) {
        return ResponseEntity.ok(issuePlaybackUrls.run(user, recordingId));
    }
}
