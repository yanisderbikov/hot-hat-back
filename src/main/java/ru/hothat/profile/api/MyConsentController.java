package ru.hothat.profile.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.hothat.config.HotHatUser;
import ru.hothat.profile.api.dto.MyConsentsResponseDTO;
import ru.hothat.profile.api.dto.RecordConsentRequestDTO;
import ru.hothat.profile.api.dto.RecordedConsentResponseDTO;
import ru.hothat.profile.usecase.GetMyConsentsUseCase;
import ru.hothat.profile.usecase.RecordConsentUseCase;

/**
 * Регистрирует принятые правовые согласия.
 *
 * <p>Заменяет действие {@code record_consent} и добавляет к нему чтение:
 * сегодня узнать, какие документы игрок принял, нельзя вовсе — поля лежат
 * в профиле и наружу не отдаются.
 *
 * <p>Единственный уровень прав в области, где гость равен игроку: правила
 * принимают все, и знать о принятом должны тоже все.
 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v2/profile/me/consents")
@Tag(name = "Profile · согласия", description = "Правовые документы, принятые учётной записью")
@SecurityRequirement(name = "Bearer")
public class MyConsentController {

    private final GetMyConsentsUseCase getMyConsents;
    private final RecordConsentUseCase recordConsent;

    @Operation(summary = "Показать свои согласия",
            description = "Если игрок ещё ничего не принимал, версии приезжают пустыми (null), "
                    + "а не шестёркой пустых строк.")
    @GetMapping
    public ResponseEntity<MyConsentsResponseDTO> get(@AuthenticationPrincipal HotHatUser user) {
        return ResponseEntity.ok(getMyConsents.run(user));
    }

    @Operation(summary = "Зарегистрировать принятие документов",
            description = "Пишет доказательство с меткой времени и отмечает допуск в профиле — "
                    + "одной транзакцией.")
    @ApiResponse(responseCode = "201", description = "Согласие записано")
    @PostMapping
    public ResponseEntity<RecordedConsentResponseDTO> record(
            @AuthenticationPrincipal HotHatUser user,
            @Valid @RequestBody RecordConsentRequestDTO request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(recordConsent.run(user, request));
    }
}
