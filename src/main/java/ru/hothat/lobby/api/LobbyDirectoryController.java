package ru.hothat.lobby.api;

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
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.hothat.common.validation.RoomId;
import ru.hothat.config.HotHatUser;
import ru.hothat.lobby.api.dto.LobbyRoomPageResponseDTO;
import ru.hothat.lobby.api.dto.LobbyRoomPreviewResponseDTO;
import ru.hothat.lobby.api.dto.LobbyRoomQueryDTO;
import ru.hothat.lobby.usecase.GetRoomPreviewUseCase;
import ru.hothat.lobby.usecase.ListOpenRoomsUseCase;

/**
 * Витрина открытых комнат.
 *
 * <p>Заменяет живую подписку браузера на коллекцию комнат
 * ({@code home/home.js:93}) и три подписки на состав выбранной комнаты
 * ({@code :88}). Подписка привозила документ каждой комнаты целиком: мешок
 * неразыгранных слов, составы, голоса апелляции и текущее слово — а решала,
 * что из этого показывать и какие комнаты вообще прятать, страница.
 *
 * <p>Открыто и гостю: лобби и превью комнаты — ровно то, что заказчик назвал
 * открытым до регистрации. Ничего сверх этого класс не отдаёт: ни входа в
 * комнату, ни чата, ни состава чужой приватной комнаты.
 *
 * <p>Старые подписки пока живы: фронтенд переедет на эти маршруты отдельно.
 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v2/lobby/rooms")
@Tag(name = "Lobby · витрина", description = "Открытые комнаты и превью одной из них")
@SecurityRequirement(name = "Bearer")
public class LobbyDirectoryController {

    private final ListOpenRoomsUseCase listOpenRooms;
    private final GetRoomPreviewUseCase getRoomPreview;

    @Operation(summary = "Показать открытые комнаты",
            description = "Комнаты, в которые сейчас можно войти или которые можно смотреть. "
                    + "Приватные, чужие тестовые и недособранные комнаты подбора отсекает сервер; "
                    + "число игроков в каждой он же и считает.")
    @GetMapping
    public ResponseEntity<LobbyRoomPageResponseDTO> list(@AuthenticationPrincipal HotHatUser user,
                                                         @ParameterObject @Valid LobbyRoomQueryDTO query) {
        return ResponseEntity.ok(listOpenRooms.run(user, query));
    }

    @Operation(summary = "Показать комнату крупным планом",
            description = "Состав, счёт и то, что происходит в комнате прямо сейчас. Текущее слово "
                    + "не отдаётся никогда — видно только последнее отгаданное.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Комната крупным планом"),
            @ApiResponse(responseCode = "404", description = "ROOM_NOT_FOUND: комнаты нет, она закрыта, "
                    + "приватна или это чужая тестовая — для зрителя это один и тот же случай",
                    content = @Content)})
    @GetMapping("/{roomId}")
    public ResponseEntity<LobbyRoomPreviewResponseDTO> preview(
            @AuthenticationPrincipal HotHatUser user,
            @Parameter(description = "Идентификатор комнаты", example = "hat-0f3a9c1d7b2e5480")
            @PathVariable @RoomId
            String roomId) {
        return ResponseEntity.ok(getRoomPreview.run(user, roomId));
    }
}
