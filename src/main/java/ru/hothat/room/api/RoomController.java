package ru.hothat.room.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.hothat.common.validation.RoomId;
import ru.hothat.config.HotHatUser;
import ru.hothat.room.api.dto.RoomSnapshotResponseDTO;
import ru.hothat.room.usecase.GetRoomSnapshotUseCase;

/**
 * Снимок комнаты для тех, кто в ней.
 *
 * <p>Заменяет четыре живые подписки браузера сразу — комната, игроки, команды,
 * зрители ({@code app-core.js:8634}, {@code :8750}, {@code :8761},
 * {@code :8806}). Четырьмя они были не по смыслу, а по устройству прежнего
 * хранилища, и приезжали вразнобой: стол успевал мигнуть пустым.
 *
 * <p>Живые изменения идут каналом {@code /ws/v2/room/{roomId}} тем же набором.
 * Этот адрес — первый снимок и починка после разрыва связи.
 *
 * <p>Уровень прав у класса один: участник либо зритель этой комнаты. Постороннему
 * состав чужой комнаты не показывают — до сих пор его привозила подписка любому,
 * кто знал идентификатор.
 *
 * <p>Старые пути пока живы: фронтенд переедет на этот маршрут отдельно.
 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v2/room/{roomId}")
@PreAuthorize("hasRole('USER')")
@Tag(name = "Room · комната", description = "Снимок комнаты для её участников")
@SecurityRequirement(name = "Bearer")
public class RoomController {

    private final GetRoomSnapshotUseCase getRoomSnapshot;

    @Operation(summary = "Показать комнату",
            description = "Паспорт, места игроков, составы команд и зрители — одним ответом. "
                    + "Мёртвые места приезжают с признаком alive=false: экран показывает их "
                    + "серыми, а не убирает, иначе стол дёргался бы при каждом пропущенном "
                    + "сердцебиении.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Снимок комнаты"),
            @ApiResponse(responseCode = "403", description = "ROOM_MEMBER_ONLY: вы не в этой комнате",
                    content = @Content),
            @ApiResponse(responseCode = "404", description = "ROOM_NOT_FOUND: комнаты нет",
                    content = @Content)})
    @GetMapping
    public ResponseEntity<RoomSnapshotResponseDTO> snapshot(
            @AuthenticationPrincipal HotHatUser user,
            @Parameter(description = "Игровая комната", example = "hat-0f3a9c1d7b2e5480")
            @PathVariable @RoomId String roomId) {
        return ResponseEntity.ok(getRoomSnapshot.run(user, roomId));
    }
}
