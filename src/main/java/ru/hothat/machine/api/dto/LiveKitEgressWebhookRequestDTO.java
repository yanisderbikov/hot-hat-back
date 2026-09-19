package ru.hothat.machine.api.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

/**
 * Уведомление LiveKit о состоянии выгрузки записи.
 *
 * <p>Сегодня тело принимается как {@code Map<String,Object>} и в спецификации
 * от него не остаётся ничего. Здесь описано то, что мы действительно читаем;
 * прочие поля LiveKit по-прежнему не мешают — Jackson их игнорирует, и это
 * правильно: чужой контракт может пополниться в любой момент.
 */
@Schema(description = "Уведомление LiveKit Egress")
public record LiveKitEgressWebhookRequestDTO(

        @Schema(description = "Имя события; принимаются только начинающиеся с egress_",
                example = "egress_ended")
        @NotBlank(message = "В уведомлении нет имени события.")
        String event,

        @Schema(description = "Состояние выгрузки", nullable = true)
        @JsonAlias({"egress_info", "egress"})
        @Valid
        LiveKitEgressInfoView egressInfo) {
}
