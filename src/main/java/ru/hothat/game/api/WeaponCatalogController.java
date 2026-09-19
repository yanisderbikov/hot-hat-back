package ru.hothat.game.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.hothat.game.api.dto.WeaponCatalogResponseDTO;
import ru.hothat.game.usecase.GetWeaponCatalogUseCase;

/**
 * Каталог диверсионного арсенала.
 *
 * <p>Адрес живёт в области партии, а не в служебной: глядя на
 * {@code /api/v2/game/weapons}, человек говорит «это про игру». Каталог питает
 * панель арсенала, и его появление отменяет копию таблицы во фронте, которая
 * уже разошлась с серверной.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v2/game/weapons")
@Tag(name = "Game · арсенал", description = "Каталог диверсионного оружия")
@SecurityRequirement(name = "Bearer")
@PreAuthorize("hasRole('USER')")
public class WeaponCatalogController {

    private final GetWeaponCatalogUseCase getCatalog;

    @Operation(summary = "Прочитать каталог оружия",
            description = "Тринадцать видов оружия, стартовый боезапас и общая перезарядка. "
                    + "Боезапас выведен из каталога, поэтому разойтись с ним не может.")
    @ApiResponse(responseCode = "200", description = "Каталог")
    @GetMapping
    public ResponseEntity<WeaponCatalogResponseDTO> catalog() {
        return ResponseEntity.ok(getCatalog.run());
    }
}
