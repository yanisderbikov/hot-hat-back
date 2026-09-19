package ru.hothat.admin.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Снимок, снятый по кнопке администратора.
 *
 * <p>Заменяет {@code POST /api/monitor} в его админской ветке. Машинный вход
 * агента мониторинга живёт отдельным адресом в {@code /api/v2/machine} и имеет
 * свой ответ: у него другое право входа и другие последствия — только агент
 * рассылает письма о превышении порогов и попадает в окно планового снятия.
 * Ручной снимок писем не шлёт и окна не проверяет, поэтому и полей про письма
 * здесь нет.
 */
@Schema(description = "Снимок расхода, снятый вручную")
public record AdminUsageSnapshotResponseDTO(

        @Schema(description = "Только что снятый и сохранённый снимок")
        UsageSnapshotView snapshot) {
}
