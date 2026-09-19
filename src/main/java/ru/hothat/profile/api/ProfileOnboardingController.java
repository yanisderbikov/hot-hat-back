package ru.hothat.profile.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.hothat.config.HotHatUser;
import ru.hothat.profile.api.dto.CompleteOnboardingRequestDTO;
import ru.hothat.profile.api.dto.CompletedOnboardingResponseDTO;
import ru.hothat.profile.api.dto.LockDivisionRequestDTO;
import ru.hothat.profile.api.dto.LockedDivisionResponseDTO;
import ru.hothat.profile.usecase.CompleteOnboardingUseCase;
import ru.hothat.profile.usecase.LockDivisionUseCase;

/**
 * Закрепляет однократный выбор игрока при первом входе.
 *
 * <p>Стоит отдельно от {@link MyProfileController} не по ресурсу, а по цене
 * ошибки: ник и аватар меняются сколько угодно, а дивизион закрепляется
 * навсегда. Обе операции здесь одноразовые, и обе отвечают 409 на повтор
 * с другим значением.
 *
 * <p>Заменяет цепочку {@code set_division → set_nickname → record_consent},
 * которую сегодня выполняет браузер тремя запросами подряд.
 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v2/profile/me")
@Tag(name = "Profile · первый вход", description = "Дивизион и онбординг: выбор, который делается один раз")
@SecurityRequirement(name = "Bearer")
public class ProfileOnboardingController {

    private final CompleteOnboardingUseCase completeOnboarding;
    private final LockDivisionUseCase lockDivision;

    @Operation(summary = "Пройти первый вход",
            description = "Дивизион, ник, аватар и правовые согласия одной транзакцией. "
                    + "Отказ на любом шаге откатывает все: учётной записи с вечным "
                    + "дивизионом и без ника после этого адреса не остаётся.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Первый вход пройден, карточка готова"),
            @ApiResponse(responseCode = "409", description = "NICKNAME_TAKEN — ник занят; "
                    + "DIVISION_LOCKED — дивизион уже закреплён за другим языком",
                    content = @io.swagger.v3.oas.annotations.media.Content)})
    @PostMapping("/onboarding")
    public ResponseEntity<CompletedOnboardingResponseDTO> complete(
            @AuthenticationPrincipal HotHatUser user,
            @Valid @RequestBody CompleteOnboardingRequestDTO request) {
        return ResponseEntity.ok(completeOnboarding.run(user, request));
    }

    @Operation(summary = "Закрепить дивизион",
            description = "Выбор однократный: сменить дивизион нельзя, иначе очки игрока "
                    + "оказались бы в таблице, где он их не набирал.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Дивизион закреплён"),
            @ApiResponse(responseCode = "409", description = "DIVISION_LOCKED — дивизион уже выбран",
                    content = @io.swagger.v3.oas.annotations.media.Content)})
    @PutMapping("/division")
    public ResponseEntity<LockedDivisionResponseDTO> lock(
            @AuthenticationPrincipal HotHatUser user,
            @Valid @RequestBody LockDivisionRequestDTO request) {
        return ResponseEntity.ok(lockDivision.run(user, request));
    }
}
