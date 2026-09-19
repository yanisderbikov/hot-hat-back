package ru.hothat.admin.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * Снимок расхода внешних сервисов и своей машины.
 *
 * <p>Общая проекция (§10.1): один и тот же снимок едет и последним известным,
 * и строкой истории, и ответом на «снять сейчас».
 *
 * <p>Раздела {@code vercel} здесь нет. В хранимых снимках он есть, но несёт
 * вечное «недоступно» с пояснением, что Vercel больше не используется, —
 * место в таблице под метрику, которой не будет никогда. Это единственное,
 * что выброшено из формы снимка.
 */
@Schema(description = "Снимок расхода ресурсов")
public record UsageSnapshotView(

        @Schema(description = "Когда снимали", example = "1788600000000", type = "integer")
        long collectedAtMs,

        @Schema(description = "День снимка, UTC", example = "2026-09-06", format = "date")
        String day,

        @Schema(description = "Месяц снимка, UTC", example = "2026-09")
        String month,

        @Schema(description = "Часовой пояс, в котором считались дневные квоты", example = "UTC")
        String quotaTimeZone,

        @Schema(description = "Расход базы")
        DatabaseUsageView database,

        @Schema(description = "Расход раздачи статики")
        HostingUsageView hosting,

        @Schema(description = "Активные пользователи за сутки")
        UsageMetricView auth,

        @Schema(description = "Расход машины")
        VpsUsageView vps,

        @Schema(description = "Расход почты")
        MailUsageView mail,

        @Schema(description = "Состояние служб на момент снимка")
        List<ServiceHealthView> health,

        @Schema(description = "Что не удалось собрать при снятии снимка; пустой список — всё собралось",
                example = "[]")
        List<String> errors) {
}
