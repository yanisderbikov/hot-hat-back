package ru.hothat.media.api;

import io.swagger.v3.oas.annotations.Operation;
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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.hothat.config.HotHatUser;
import ru.hothat.media.api.dto.DeleteOrphanFileRequestDTO;
import ru.hothat.media.api.dto.PlaybackTicketResponseDTO;
import ru.hothat.media.api.dto.PosterUploadTicketResponseDTO;
import ru.hothat.media.api.dto.RequestPlaybackTicketRequestDTO;
import ru.hothat.media.api.dto.RequestPosterUploadTicketRequestDTO;
import ru.hothat.media.api.dto.RequestVideoUploadTicketRequestDTO;
import ru.hothat.media.api.dto.VideoUploadTicketResponseDTO;
import ru.hothat.media.usecase.DeleteOrphanFileUseCase;
import ru.hothat.media.usecase.IssuePlaybackTicketUseCase;
import ru.hothat.media.usecase.IssuePosterUploadTicketUseCase;
import ru.hothat.media.usecase.IssueVideoUploadTicketUseCase;

/**
 * Обслуживает прямой доступ браузера к файлам мемов.
 *
 * <p>Байты мимо бекенда: и загрузка, и воспроизведение идут по подписанным
 * ссылкам, которые выдаёт этот класс. Сервер не гоняет через себя восемь
 * мегабайт на каждый ролик и не платит за трафик дважды.
 *
 * <p>Заменяет {@code POST /api/media}, где одно тело с полем {@code action}
 * выбирало между тремя операциями, а внутри загрузки поле {@code kind}
 * выбирало ещё между двумя. Разрез сделан честно, по разнице, а не по
 * названию: у загрузки ролика, загрузки заставки и воспроизведения разные
 * пути объектов, разные допустимые типы содержимого, разные пределы, разные
 * сроки жизни подписи и разные коды ошибок. Единственное общее — слово
 * «билет», и его мало, чтобы держать их в одной схеме.
 *
 * <p>Старый адрес пока жив: фронтенд переедет на эти маршруты отдельно.
 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v2/media")
@Tag(name = "Media · билеты", description = "Подписанные ссылки на загрузку и воспроизведение файлов мемов")
@SecurityRequirement(name = "Bearer")
public class MediaFileAccessController {

    private final IssueVideoUploadTicketUseCase issueVideoUploadTicket;
    private final IssuePosterUploadTicketUseCase issuePosterUploadTicket;
    private final IssuePlaybackTicketUseCase issuePlaybackTicket;
    private final DeleteOrphanFileUseCase deleteOrphanFile;

    @Operation(summary = "Взять билет на загрузку ролика",
            description = "Подписанная ссылка на десять минут. Путь объекта строит сервер: в нём "
                    + "дивизион, игрок и мем. Заголовок Content-Type при PUT обязан совпасть с тем, "
                    + "что вернул билет, — подпись считается по нему.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Билет выдан"),
            @ApiResponse(responseCode = "503", description = "S3_MEDIA_STORAGE_NOT_CONFIGURED: хранилище "
                    + "не настроено", content = @Content)})
    @PostMapping("/tickets/video-uploads")
    public ResponseEntity<VideoUploadTicketResponseDTO> issueVideoUpload(
            @AuthenticationPrincipal HotHatUser user,
            @Valid @RequestBody RequestVideoUploadTicketRequestDTO request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(issueVideoUploadTicket.run(user, request));
    }

    @Operation(summary = "Взять билет на загрузку заставки",
            description = "То же, что для ролика, но со своим списком типов и пределом в один мегабайт: "
                    + "заставка едет вместе с каждой карточкой библиотеки.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Билет выдан"),
            @ApiResponse(responseCode = "503", description = "S3_MEDIA_STORAGE_NOT_CONFIGURED: хранилище "
                    + "не настроено", content = @Content)})
    @PostMapping("/tickets/poster-uploads")
    public ResponseEntity<PosterUploadTicketResponseDTO> issuePosterUpload(
            @AuthenticationPrincipal HotHatUser user,
            @Valid @RequestBody RequestPosterUploadTicketRequestDTO request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(issuePosterUploadTicket.run(user, request));
    }

    @Operation(summary = "Взять ссылку на воспроизведение",
            description = "Подписанная ссылка на два часа: столько живёт кеш браузера вместе с "
                    + "range-запросами. Ничего не создаёт — отсюда 200, а не 201.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Ссылка выдана"),
            @ApiResponse(responseCode = "403", description = "MEDIA_PATH_INVALID: путь не похож на файл мема",
                    content = @Content),
            @ApiResponse(responseCode = "503", description = "S3_MEDIA_STORAGE_NOT_CONFIGURED: хранилище "
                    + "не настроено", content = @Content)})
    @PostMapping("/tickets/playbacks")
    public ResponseEntity<PlaybackTicketResponseDTO> issuePlayback(
            @AuthenticationPrincipal HotHatUser user,
            @Valid @RequestBody RequestPlaybackTicketRequestDTO request) {
        return ResponseEntity.ok(issuePlaybackTicket.run(user, request));
    }

    @Operation(summary = "Убрать свой осиротевший файл",
            description = "Файл уехал в хранилище, а публикация не удалась. Владелец определяется по "
                    + "самому ключу: свой идентификатор сервер положил в него при выдаче билета.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Файла больше нет"),
            @ApiResponse(responseCode = "403", description = "MEDIA_PATH_INVALID: файл лежит не в вашей папке",
                    content = @Content)})
    @DeleteMapping("/orphan-files")
    public ResponseEntity<Void> deleteOrphan(
            @AuthenticationPrincipal HotHatUser user,
            @Valid @RequestBody DeleteOrphanFileRequestDTO request) {
        deleteOrphanFile.run(user, request);
        return ResponseEntity.noContent().build();
    }
}
