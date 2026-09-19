package ru.hothat.profile.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
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
import ru.hothat.profile.api.dto.ConsumeSabotageEntitlementRequestDTO;
import ru.hothat.profile.api.dto.ConsumedSabotageEntitlementResponseDTO;
import ru.hothat.profile.api.dto.DefaultLoadoutResponseDTO;
import ru.hothat.profile.api.dto.SabotageEntitlementResponseDTO;
import ru.hothat.profile.api.dto.SaveDefaultLoadoutRequestDTO;
import ru.hothat.profile.api.dto.SavedDefaultLoadoutResponseDTO;
import ru.hothat.profile.usecase.ConsumeSabotageEntitlementUseCase;
import ru.hothat.profile.usecase.GetDefaultLoadoutUseCase;
import ru.hothat.profile.usecase.GetSabotageEntitlementUseCase;
import ru.hothat.profile.usecase.SaveDefaultLoadoutUseCase;

/**
 * Диверсионное снаряжение учётной записи: обойма мемов и квота партий.
 *
 * <p>Оба хозяйства вынуты из {@code ensure_profile}, где ехали вложенными
 * блоками рядом с ником и аватаром. Обойма к тому же не имела адреса вовсе:
 * браузер писал {@code defaultMemeLoadout} прямо в документ профиля через
 * прежний документный шлюз. Из-за этого поверхность v2 была непроходима —
 * комнату не создать без пяти заряженных мемов, а зарядить их было нечем.
 *
 * <p>Обойма и квота стоят рядом, потому что вместе решают один вопрос:
 * пустят ли игрока в режим диверсий. Класс со временем переедет в
 * {@code ru.hothat.sabotage.api}, сохранив адреса: путь называет владельца
 * учётной записи, пакет — владельца правил (§4.2 плана). Счётчик обоймы
 * дублируется в карточке профиля — главной странице хватает числа, пять
 * идентификаторов ей ни к чему.
 *
 * <p>Уровень прав один на класс: снаряжение своей учётной записи ведёт
 * только её владелец.
 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v2/profile/me")
@Tag(name = "Profile · снаряжение", description = "Обойма мемов и квота диверсий текущего игрока")
@SecurityRequirement(name = "Bearer")
public class MyLoadoutController {

    private final GetDefaultLoadoutUseCase getDefaultLoadout;
    private final SaveDefaultLoadoutUseCase saveDefaultLoadout;
    private final GetSabotageEntitlementUseCase getSabotageEntitlement;
    private final ConsumeSabotageEntitlementUseCase consumeSabotageEntitlement;

    @Operation(summary = "Показать обойму мемов",
            description = "Состав по порядку слотов и готовность. Отдаётся уже очищенный "
                    + "состав — тот же, который заряжается в партию.")
    @ApiResponse(responseCode = "200", description = "Обойма учётной записи")
    @GetMapping("/meme-loadout")
    public ResponseEntity<DefaultLoadoutResponseDTO> loadout(@AuthenticationPrincipal HotHatUser user) {
        return ResponseEntity.ok(getDefaultLoadout.run(user));
    }

    @Operation(summary = "Задать обойму мемов",
            description = "Обойма заменяется целиком. Меньше пяти мемов принимается: экран "
                    + "арсенала сохраняет выбор на каждом щелчке, и собрать обойму иначе было бы "
                    + "нельзя. Готовность видна в ответе; с неполной обоймой в комнату не пустят "
                    + "(409 DEFAULT_LOADOUT_REQUIRED на входе в игру).")
    @ApiResponse(responseCode = "200", description = "Обойма сохранена; в ответе — что именно сохранено")
    @PutMapping("/meme-loadout")
    public ResponseEntity<SavedDefaultLoadoutResponseDTO> saveLoadout(
            @AuthenticationPrincipal HotHatUser user,
            @Valid @RequestBody SaveDefaultLoadoutRequestDTO request) {
        return ResponseEntity.ok(saveDefaultLoadout.run(user, request));
    }

    @Operation(summary = "Показать квоту диверсий",
            description = "Пять бесплатных партий на учётную запись; у владельца сервиса "
                    + "и у его друзей ограничения нет.")
    @GetMapping("/sabotage-entitlement")
    public ResponseEntity<SabotageEntitlementResponseDTO> entitlement(@AuthenticationPrincipal HotHatUser user) {
        return ResponseEntity.ok(getSabotageEntitlement.run(user));
    }

    @Operation(summary = "Списать партию из квоты диверсий",
            description = "Зовётся сразу после старта партии с диверсиями. Повтор за ту же "
                    + "партию не тратит вторую: ключ списания сервер складывает из комнаты, "
                    + "номера партии и игрока.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Квота учтена; в ответе — остаток после"),
            @ApiResponse(responseCode = "402", description = "SABOTAGE_LIMIT_REACHED — бесплатные "
                    + "партии закончились", content = @Content)})
    @PostMapping("/sabotage-entitlement/consumptions")
    public ResponseEntity<ConsumedSabotageEntitlementResponseDTO> consume(
            @AuthenticationPrincipal HotHatUser user,
            @Valid @RequestBody ConsumeSabotageEntitlementRequestDTO request) {
        return ResponseEntity.ok(consumeSabotageEntitlement.run(user, request));
    }
}
