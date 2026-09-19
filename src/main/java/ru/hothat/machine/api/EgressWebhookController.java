package ru.hothat.machine.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.hothat.common.validation.RoomId;
import ru.hothat.machine.api.dto.EgressWebhookAckResponseDTO;
import ru.hothat.machine.api.dto.LiveKitEgressWebhookRequestDTO;
import ru.hothat.machine.security.MachineSecurityConfig;
import ru.hothat.machine.usecase.ApplyEgressWebhookUseCase;

/**
 * Принимает уведомление LiveKit о выгрузке записи.
 *
 * <p>Заменяет {@code POST /api/recording-egress?sig=…}. Изменилось два места.
 * Первое: удостоверяет запрос подпись самого LiveKit из заголовка
 * {@code Authorization} — та, ради которой мы кладём в заявку на запись
 * {@code signing_key} и которую сегодня не проверяем ни разу. Второе:
 * комната и партия стоят в пути, а не в строке запроса.
 *
 * <p>Ответ всегда 200 — даже когда уведомление не применили. LiveKit повторяет
 * доставку при неуспехе, и «мы это событие сознательно пропустили» не повод
 * заставлять его повторять; причина приезжает полем {@code outcome}.
 *
 * <p>Старый адрес пока жив: на него настроены уже идущие выгрузки, и
 * переключение — отдельный шаг эксплуатации.
 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v2/machine/webhooks/livekit-egress")
@Tag(name = "Machine · вебхук Egress", description = "Уведомления LiveKit о выгрузке записи")
@SecurityRequirement(name = MachineSecurityConfig.LIVEKIT_SCHEME)
public class EgressWebhookController {

    private final ApplyEgressWebhookUseCase applyEgressWebhook;

    @Operation(summary = "Принять уведомление о выгрузке",
            description = "Обновляет состояние записи по событию LiveKit. События не про выгрузку "
                    + "и уведомления от чужой выгрузки той же партии не применяются.")
    @ApiResponse(responseCode = "200", description = "Уведомление принято или сознательно пропущено")
    @PostMapping("/rooms/{roomId}/games/{gameNumber}")
    public ResponseEntity<EgressWebhookAckResponseDTO> apply(
            @Parameter(description = "Комната, чья запись выгружается")
            @PathVariable @RoomId String roomId,
            @Parameter(description = "Номер записанной партии")
            @PathVariable @Min(value = 0, message = "Номер партии не может быть отрицательным.") int gameNumber,
            @Valid @RequestBody LiveKitEgressWebhookRequestDTO request) {
        return ResponseEntity.ok(applyEgressWebhook.run(roomId, gameNumber, request));
    }
}
