package ru.hothat.app.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.hothat.app.api.dto.FeatureFlagResponseDTO;
import ru.hothat.app.api.validation.FeatureFlagName;
import ru.hothat.app.usecase.ReadFeatureFlagUseCase;

/**
 * Рубильники возможностей.
 *
 * <p>Заменяет {@code GET /api/features/{name}} ({@code features.js:54}).
 * Спрашивают по одному имени; списка наружу нет и не будет — набор
 * возможностей продукта не должен утекать тому, кто просто открыл главную.
 *
 * <p>Переключает флаги консоль администратора, у неё свой адрес и свои права.
 * Здесь только чтение — иначе на классе оказалось бы два уровня прав.
 *
 * <p>Старый адрес пока жив: фронтенд переедет на этот маршрут отдельно.
 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v2/app/features")
@Tag(name = "App · возможности", description = "Чтение рубильников возможностей")
@SecurityRequirements
public class FeatureFlagController {

    private final ReadFeatureFlagUseCase readFeatureFlag;

    @Operation(summary = "Включена ли возможность",
            description = "Имя — из закрытого набора. Незнакомое имя отвергается: раньше оно "
                    + "отвечало «выключено» и заводило вечную запись в кеше на каждое "
                    + "выдуманное имя.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Состояние возможности"),
            @ApiResponse(responseCode = "400", description = "VALIDATION_FAILED: имя вне закрытого набора",
                    content = @Content)})
    @GetMapping("/{name}")
    public ResponseEntity<FeatureFlagResponseDTO> isEnabled(
            // Только description: springdoc, увидев вложенный @Schema, строит схему
            // из аннотации и теряет Java-тип — параметр уезжал в спецификацию
            // без type. Закрытый набор имён и так проверяет @FeatureFlagName,
            // а перечислить его для читателя хватает описания.
            @Parameter(description = "Имя возможности: bot_enabled или admin_feature",
                    example = "bot_enabled")
            @PathVariable @FeatureFlagName
            String name) {
        return ResponseEntity.ok(readFeatureFlag.run(name));
    }
}
