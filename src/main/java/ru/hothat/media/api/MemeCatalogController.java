package ru.hothat.media.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.hothat.config.HotHatUser;
import ru.hothat.media.api.dto.MemeCardResponseDTO;
import ru.hothat.media.api.dto.MemeCatalogPageResponseDTO;
import ru.hothat.media.api.dto.MemeCatalogQueryDTO;
import ru.hothat.media.api.dto.PublishMemeRequestDTO;
import ru.hothat.media.api.dto.PublishedMemeResponseDTO;
import ru.hothat.media.api.validation.MemeId;
import ru.hothat.media.usecase.GetMemeUseCase;
import ru.hothat.media.usecase.ListMemesUseCase;
import ru.hothat.media.usecase.PublishMemeUseCase;
import ru.hothat.media.usecase.WithdrawMyMemeUseCase;

/**
 * Ведёт библиотеку мемов.
 *
 * <p>Заменяет четыре обращения браузера к документному шлюзу: запрос
 * коллекции {@code memeLibrary} с живой подпиской ({@code app-core.js:5969}),
 * точечное чтение мема мимо кеша ({@code :5184}) и запись карточки
 * ({@code :6424}). Библиотека перестаёт быть таблицей, в которую пишет
 * клиент, и становится ресурсом: что в карточке правда, решает сервер.
 *
 * <p>Снятие мема администратором сюда не входит — у него другая аудитория и
 * другое право, он живёт в {@code /api/v2/admin/memes}.
 *
 * <p>Старые адреса пока живы: фронтенд переедет на эти маршруты отдельно.
 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v2/media/memes")
@Tag(name = "Media · библиотека", description = "Общая библиотека мем-роликов")
@SecurityRequirement(name = "Bearer")
public class MemeCatalogController {

    private final ListMemesUseCase listMemes;
    private final GetMemeUseCase getMeme;
    private final PublishMemeUseCase publishMeme;
    private final WithdrawMyMemeUseCase withdrawMyMeme;

    @Operation(summary = "Показать библиотеку мемов",
            description = "Карточки мемов, доступных для обоймы. Снятые с публикации не отдаются.")
    @GetMapping
    public ResponseEntity<MemeCatalogPageResponseDTO> list(@AuthenticationPrincipal HotHatUser user,
                                                           @ParameterObject @Valid MemeCatalogQueryDTO query) {
        return ResponseEntity.ok(listMemes.run(user, query));
    }

    @Operation(summary = "Показать один мем",
            description = "Догрузка карточки мимо кеша: мем выпустили диверсией, а в загруженной "
                    + "библиотеке его нет.")
    // 200 объявлен явно: пока среди ответов был один лишь 404, springdoc не
    // добавлял к нему успешный, и в спецификации у этого адреса не оставалось
    // ни кода 200, ни схемы MemeCardResponseDTO вообще.
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Карточка мема"),
            @ApiResponse(responseCode = "404", description = "MEME_NOT_FOUND: мема нет или он снят с публикации",
                    content = @Content)})
    @GetMapping("/{memeId}")
    public ResponseEntity<MemeCardResponseDTO> get(
            @AuthenticationPrincipal HotHatUser user,
            @Parameter(description = "Идентификатор мема", example = "meme-9f31ab77c204")
            @PathVariable @MemeId String memeId) {
        return ResponseEntity.ok(getMeme.run(user, memeId));
    }

    @Operation(summary = "Опубликовать мем",
            description = "Файлы к этому моменту уже в хранилище — сюда приезжает только карточка. "
                    + "Автора, адреса файлов, тип и размер ролика ставит сервер. Повтор с тем же "
                    + "идентификатором — та же публикация, а не вторая.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Мем в библиотеке"),
            @ApiResponse(responseCode = "403", description = "NOT_MEME_AUTHOR: этот идентификатор занят "
                    + "чужой карточкой; MEDIA_PATH_INVALID: файл лежит не в вашей папке", content = @Content),
            @ApiResponse(responseCode = "409", description = "MEME_MEDIA_MISSING: ролика по этому ключу "
                    + "в хранилище нет — загрузка не доехала", content = @Content),
            @ApiResponse(responseCode = "503", description = "S3_MEDIA_STORAGE_NOT_CONFIGURED: хранилище "
                    + "не настроено", content = @Content)})
    @PostMapping
    public ResponseEntity<PublishedMemeResponseDTO> publish(
            @AuthenticationPrincipal HotHatUser user,
            @Valid @RequestBody PublishMemeRequestDTO request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(publishMeme.run(user, request));
    }

    @Operation(summary = "Снять свой мем с публикации",
            description = "Убирает карточку из библиотеки и файлы из хранилища. Доступно автору мема.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Мема больше нет"),
            @ApiResponse(responseCode = "403", description = "NOT_MEME_AUTHOR: мем выложил не вы; "
                    + "BUILTIN_MEME_PROTECTED: встроенный мем снять нельзя", content = @Content),
            @ApiResponse(responseCode = "404", description = "MEME_NOT_FOUND: мема нет", content = @Content)})
    @DeleteMapping("/{memeId}")
    public ResponseEntity<Void> withdraw(
            @AuthenticationPrincipal HotHatUser user,
            @Parameter(description = "Идентификатор своего мема", example = "meme-9f31ab77c204")
            @PathVariable @MemeId String memeId) {
        withdrawMyMeme.run(user, memeId);
        return ResponseEntity.noContent().build();
    }
}
