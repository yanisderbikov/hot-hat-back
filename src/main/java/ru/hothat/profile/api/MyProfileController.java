package ru.hothat.profile.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.hothat.config.HotHatUser;
import ru.hothat.profile.api.dto.ClaimNicknameRequestDTO;
import ru.hothat.profile.api.dto.ClaimedNicknameResponseDTO;
import ru.hothat.profile.api.dto.ChangeUiLanguageRequestDTO;
import ru.hothat.profile.api.dto.MyProfileResponseDTO;
import ru.hothat.profile.api.dto.ReplaceAvatarRequestDTO;
import ru.hothat.profile.api.dto.StoredAvatarResponseDTO;
import ru.hothat.profile.api.dto.SubmitNicknameRequestDTO;
import ru.hothat.profile.api.dto.SubmittedNicknameRequestResponseDTO;
import ru.hothat.profile.api.dto.UiLanguageResponseDTO;
import ru.hothat.profile.usecase.ChangeUiLanguageUseCase;
import ru.hothat.profile.usecase.ClaimNicknameUseCase;
import ru.hothat.profile.usecase.GetMyProfileUseCase;
import ru.hothat.profile.usecase.ReplaceAvatarUseCase;
import ru.hothat.profile.usecase.SubmitNicknameRequestUseCase;

/**
 * Правит карточку текущего игрока.
 *
 * <p>Заменяет пять действий {@code POST /api/portal}: {@code ensure_profile},
 * {@code set_nickname}, {@code set_avatar}, {@code set_ui_language},
 * {@code request_nickname}. Все пять приходили одним телом с полем
 * {@code action} и возвращали пять несовместимых объектов под общей схемой
 * {@code Map<String,Object>} — в спецификации от них не было ни одного поля.
 *
 * <p>Однократный выбор игрока — дивизион и первый вход — живёт в соседнем
 * {@link ProfileOnboardingController}: там другая цена ошибки.
 *
 * <p>Старый адрес пока жив: фронтенд переедет на эти маршруты отдельно.
 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v2/profile/me")
@Tag(name = "Profile · карточка", description = "Ник, аватар и язык интерфейса текущего игрока")
@SecurityRequirement(name = "Bearer")
public class MyProfileController {

    private final GetMyProfileUseCase getMyProfile;
    private final ClaimNicknameUseCase claimNickname;
    private final ReplaceAvatarUseCase replaceAvatar;
    private final ChangeUiLanguageUseCase changeUiLanguage;
    private final SubmitNicknameRequestUseCase submitNicknameRequest;

    @Operation(summary = "Показать свой профиль",
            description = "Первый заход заводит профиль и выдаёт сгенерированный ник, "
                    + "поэтому пустой карточки в ответе не бывает.")
    @GetMapping
    public ResponseEntity<MyProfileResponseDTO> get(@AuthenticationPrincipal HotHatUser user) {
        return ResponseEntity.ok(getMyProfile.run(user));
    }

    @Operation(summary = "Занять ник")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Ник закреплён за игроком"),
            @ApiResponse(responseCode = "409", description = "NICKNAME_TAKEN — ник занят другой учётной записью",
                    content = @io.swagger.v3.oas.annotations.media.Content)})
    @PutMapping("/nickname")
    public ResponseEntity<ClaimedNicknameResponseDTO> claim(
            @AuthenticationPrincipal HotHatUser user,
            @Valid @RequestBody ClaimNicknameRequestDTO request) {
        return ResponseEntity.ok(claimNickname.run(user, request));
    }

    @Operation(summary = "Заменить аватар",
            description = "Картинка приезжает data-URL'ом и хранится строкой, поэтому длина ограничена.")
    @PutMapping("/avatar")
    public ResponseEntity<StoredAvatarResponseDTO> replace(
            @AuthenticationPrincipal HotHatUser user,
            @Valid @RequestBody ReplaceAvatarRequestDTO request) {
        return ResponseEntity.ok(replaceAvatar.run(user, request));
    }

    @Operation(summary = "Переключить язык интерфейса",
            description = "Допустимы язык своего дивизиона и английский; о третьем "
                    + "сервер отвечает языком дивизиона, и в ответе видно оба значения.")
    @PutMapping("/ui-language")
    public ResponseEntity<UiLanguageResponseDTO> changeLanguage(
            @AuthenticationPrincipal HotHatUser user,
            @Valid @RequestBody ChangeUiLanguageRequestDTO request) {
        return ResponseEntity.ok(changeUiLanguage.run(user, request));
    }

    @Operation(summary = "Подать заявку на занятый ник",
            description = "Заявку разбирает владелец сервиса вручную; автоматического решения нет.")
    @ApiResponse(responseCode = "201", description = "Заявка принята к разбору")
    @PostMapping("/nickname-requests")
    public ResponseEntity<SubmittedNicknameRequestResponseDTO> submitRequest(
            @AuthenticationPrincipal HotHatUser user,
            @Valid @RequestBody SubmitNicknameRequestDTO request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(submitNicknameRequest.run(user, request));
    }
}
