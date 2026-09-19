package ru.hothat.profile.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.hothat.common.validation.PlayerUid;
import ru.hothat.profile.api.dto.PublicPlayerResponseDTO;
import ru.hothat.profile.usecase.GetPublicPlayerUseCase;

/**
 * Показывает публичную карточку игрока.
 *
 * <p>Заменяет действие {@code public_player_profile}. Отдельный класс — потому
 * что здесь другой ресурс и другая граница: сведения о чужом человеке, а не
 * о себе, и набор полей урезан до того, что игрок сам показывает другим.
 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v2/profile/players")
@Tag(name = "Profile · публичный профиль", description = "Карточка чужого игрока")
@SecurityRequirement(name = "Bearer")
public class PublicPlayerController {

    private final GetPublicPlayerUseCase getPublicPlayer;

    @Operation(summary = "Показать публичный профиль игрока",
            description = "Ник, аватар, дивизион, постоянная команда и рейтинги текущего сезона. "
                    + "Почты и служебных полей учётной записи здесь нет.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Профиль найден"),
            @ApiResponse(responseCode = "404", description = "PLAYER_NOT_FOUND — такого игрока нет",
                    content = @io.swagger.v3.oas.annotations.media.Content)})
    @GetMapping("/{uid}")
    public ResponseEntity<PublicPlayerResponseDTO> get(
            @Parameter(description = "Идентификатор игрока", example = "8f3a2b1c9d4e7a6b5c0d1e2f")
            @PathVariable @PlayerUid String uid) {
        return ResponseEntity.ok(getPublicPlayer.run(uid));
    }
}
