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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.hothat.common.validation.RoomId;
import ru.hothat.config.HotHatUser;
import ru.hothat.room.api.dto.HostHandoverResponseDTO;
import ru.hothat.room.usecase.HandOverIdleHostUseCase;

/**
 * Сторож бездействующего хозяина.
 *
 * <p>Заменяет {@code setup_host_watch} ({@code GameServiceImpl:392}) и чинит
 * две вещи. Права: тот метод был единственным в срезе без проверки участия,
 * хотя писал {@code room.createdBy} (находка A6). Форма ответа: пять
 * несовместимых карт свелись к одной записи с полем-перечислением (замечание
 * C6) — клиент перестаёт различать исходы по наличию ключей.
 *
 * <p>Отдельный класс от хозяйских распоряжений именно из-за прав: спрашивает
 * сторожа не хозяин, а участник. Хозяин, проверяющий сам себя на бездействие,
 * бездействующим не бывает.
 *
 * <p>{@code POST}, а не {@code GET}: вызов может сменить хозяина комнаты, и
 * прятать это за читающим методом было бы неправдой.
 *
 * <p>Старый путь пока жив: фронтенд переедет на этот маршрут отдельно.
 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v2/room/{roomId}/host/handover")
@PreAuthorize("hasRole('USER')")
@Tag(name = "Room · сторож хозяйства", description = "Передача осиротевшего хозяйства комнаты")
@SecurityRequirement(name = "Bearer")
public class RoomHostHandoverController {

    private final HandOverIdleHostUseCase handOverIdleHost;

    @Operation(summary = "Спросить сторожа хозяйства",
            description = "Если комната укомплектована, а хозяин три минуты ничего не делает, "
                    + "права переходят следующему активному участнику. Вернувшийся прежний хозяин "
                    + "получает комнату обратно — один раз: передача по бездействию окончательна. "
                    + "Приватную комнату сторож не трогает.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Решение сторожа"),
            @ApiResponse(responseCode = "403", description = "ROOM_MEMBER_ONLY: вы не в этой комнате",
                    content = @Content),
            @ApiResponse(responseCode = "404", description = "ROOM_NOT_FOUND: комнаты нет",
                    content = @Content)})
    @PostMapping
    public ResponseEntity<HostHandoverResponseDTO> handover(
            @AuthenticationPrincipal HotHatUser user,
            @Parameter(description = "Игровая комната", example = "hat-0f3a9c1d7b2e5480")
            @PathVariable @RoomId String roomId) {
        return ResponseEntity.ok(handOverIdleHost.run(user, roomId));
    }
}
