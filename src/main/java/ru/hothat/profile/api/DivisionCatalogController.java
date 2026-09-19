package ru.hothat.profile.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.hothat.profile.api.dto.DivisionCatalogResponseDTO;
import ru.hothat.profile.api.dto.DivisionSuggestionResponseDTO;
import ru.hothat.profile.usecase.ListDivisionsUseCase;
import ru.hothat.profile.usecase.SuggestDivisionUseCase;

/**
 * Отдаёт справочник языковых дивизионов.
 *
 * <p>Заменяет {@code GET /api/geo} и снимает третью копию списка языков:
 * сегодня он продублирован в {@code util/Divisions}, в проверке доступа к
 * документам и во фронтенде, а расхождение любых двух копий означает игрока,
 * которому показали дивизион, куда его не пустят.
 *
 * <p>Оба адреса открыты: справочник нужен экрану регистрации, где токена
 * ещё нет. Секретов в ответах нет.
 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v2/profile/divisions")
@Tag(name = "Profile · дивизионы", description = "Справочник языковых дивизионов")
@SecurityRequirements
public class DivisionCatalogController {

    private final ListDivisionsUseCase listDivisions;
    private final SuggestDivisionUseCase suggestDivision;

    @Operation(summary = "Перечислить дивизионы",
            description = "Девять языков в порядке кода. Набор закрыт и на страницы не делится.")
    @GetMapping
    public ResponseEntity<DivisionCatalogResponseDTO> list() {
        return ResponseEntity.ok(listDivisions.run());
    }

    @Operation(summary = "Подсказать дивизион по стране",
            description = "Страну сообщает обратный прокси-сервер; браузер не спрашивают. "
                    + "Это подсказка для поля выбора — закрепляет дивизион только явное действие игрока.")
    @GetMapping("/suggestion")
    public ResponseEntity<DivisionSuggestionResponseDTO> suggest(
            @Parameter(description = "Код страны от Cloudflare; главнее второго заголовка", example = "RU")
            @RequestHeader(value = "CF-IPCountry", required = false) String cloudflareCountry,
            @Parameter(description = "Код страны от своего балансировщика", example = "RU")
            @RequestHeader(value = "X-Country-Code", required = false) String proxyCountry) {
        return ResponseEntity.ok(suggestDivision.run(cloudflareCountry, proxyCountry));
    }
}
