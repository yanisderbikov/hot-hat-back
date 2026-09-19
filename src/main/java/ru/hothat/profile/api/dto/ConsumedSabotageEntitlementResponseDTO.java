package ru.hothat.profile.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Итог списания партии из квоты диверсий.
 *
 * <p>Одна форма ответа на все три исхода. Списали партию, повторили уже
 * зачтённый запрос, у игрока безлимит — во всех случаях клиенту важно одно
 * и то же: сколько осталось после. Разница выражена признаком
 * {@code consumed}, а не разными телами ответа: старое действие отвечало
 * {@code ok:true} и на списание, и на повтор, и отличить их было нечем.
 */
@Schema(description = "Итог списания партии из квоты диверсий")
public record ConsumedSabotageEntitlementResponseDTO(

        @Schema(description = "Списалась ли партия именно этим запросом. false — при безлимите "
                + "и при повторе уже зачтённой партии: обе не ошибка",
                example = "true", type = "boolean")
        boolean consumed,

        @Schema(description = "Состояние квоты после списания")
        SabotageEntitlementView entitlement) {
}
