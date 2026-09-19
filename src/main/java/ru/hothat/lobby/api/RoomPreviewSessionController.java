package ru.hothat.lobby.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.hothat.common.validation.RoomId;
import ru.hothat.config.HotHatUser;
import ru.hothat.lobby.api.dto.RoomPreviewHeartbeatResponseDTO;
import ru.hothat.lobby.api.dto.RoomPreviewSessionResponseDTO;
import ru.hothat.lobby.usecase.CloseRoomPreviewUseCase;
import ru.hothat.lobby.usecase.OpenRoomPreviewUseCase;
import ru.hothat.lobby.usecase.TouchRoomPreviewUseCase;

/**
 * Сессия видеопревью комнаты: та самая картинка на главной.
 *
 * <p>Заменяет три записи, которые браузер делал в базу сам: заведение места
 * наблюдателя ({@code live-preview.js:101}), продление его времени
 * ({@code :102}) и удаление при уходе ({@code app-core.js:4852}) — плюс
 * отдельный поход за видеотокеном ({@code live-preview.js:104}). Токен и
 * место выдаются теперь одной операцией: раньше между ними жило состояние
 * «зритель есть, токена нет», и убирать за передумавшим клиентом было некому.
 *
 * <p>Открыто гостю: превью комнаты названо заказчиком среди того, что видно
 * до регистрации. Токен даётся только на приём — публиковать дорожки им
 * нельзя, в приватную комнату он не пускает.
 *
 * <p>Тела ни у одной из трёх операций нет, и это не упущение: выбирать в них
 * нечего. Имя наблюдателя сервер знает из учётки, идентификатор сессии выдаёт
 * сам, комнату называет адрес. Пустая запись-запрос ради симметрии была бы
 * приглашением однажды положить в неё что-нибудь, чему там не место.
 *
 * <p>Старые записи пока живы: фронтенд переедет на эти маршруты отдельно.
 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v2/lobby/rooms/{roomId}/preview-session")
@Tag(name = "Lobby · превью", description = "Сессия видеопревью комнаты")
@SecurityRequirement(name = "Bearer")
public class RoomPreviewSessionController {

    private final OpenRoomPreviewUseCase openRoomPreview;
    private final TouchRoomPreviewUseCase touchRoomPreview;
    private final CloseRoomPreviewUseCase closeRoomPreview;

    @Operation(summary = "Открыть превью комнаты",
            description = "Заводит место наблюдателя и выдаёт видеотокен только на приём.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Сессия открыта, токен выдан"),
            @ApiResponse(responseCode = "404", description = "ROOM_NOT_FOUND: смотреть нечего — комнаты "
                    + "нет, она закрыта, приватна или это чужая тестовая", content = @Content),
            @ApiResponse(responseCode = "503", description = "LIVEKIT_NOT_CONFIGURED: видеосвязь не настроена",
                    content = @Content)})
    @PostMapping
    public ResponseEntity<RoomPreviewSessionResponseDTO> open(
            @AuthenticationPrincipal HotHatUser user,
            @Parameter(description = "Комната, которую смотрим", example = "hat-0f3a9c1d7b2e5480")
            @PathVariable @RoomId
            String roomId) {
        return ResponseEntity.status(HttpStatus.CREATED).body(openRoomPreview.run(user, roomId));
    }

    @Operation(summary = "Подтвердить, что превью открыто",
            description = "Отметку времени ставит сервер: у клиента часы могут уехать.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Место наблюдателя продлено"),
            @ApiResponse(responseCode = "404", description = "ROOM_NOT_FOUND: комнаты больше нет; "
                    + "PREVIEW_SESSION_NOT_FOUND: сессию уже закрыли — откройте новую",
                    content = @Content)})
    @PutMapping
    public ResponseEntity<RoomPreviewHeartbeatResponseDTO> touch(
            @AuthenticationPrincipal HotHatUser user,
            @Parameter(description = "Комната, которую смотрим", example = "hat-0f3a9c1d7b2e5480")
            @PathVariable @RoomId
            String roomId) {
        return ResponseEntity.ok(touchRoomPreview.run(user, roomId));
    }

    @Operation(summary = "Закрыть превью комнаты",
            description = "Убирает место наблюдателя. Настоящее зрительское место, если человек "
                    + "смотрит ту же комнату с игрового экрана, не трогает.")
    @ApiResponse(responseCode = "204", description = "Превью закрыто; закрывать было нечего — тот же ответ")
    @DeleteMapping
    public ResponseEntity<Void> close(
            @AuthenticationPrincipal HotHatUser user,
            @Parameter(description = "Комната, которую смотрели", example = "hat-0f3a9c1d7b2e5480")
            @PathVariable @RoomId
            String roomId) {
        closeRoomPreview.run(user, roomId);
        return ResponseEntity.noContent().build();
    }
}
