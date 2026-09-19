package ru.hothat.profile.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * Справочник дивизионов.
 *
 * <p>Сегодня этот список продублирован трижды: в {@code util/Divisions},
 * в проверке доступа к документам и во фронтенде. Расхождение любых двух копий
 * означает игрока, которому показали дивизион, куда его не пустят.
 *
 * <p>Форма списка общая для всего API — {@code items}, курсор и предел, — хотя
 * набор здесь закрыт и никогда не делится на страницы: клиенту не нужно помнить,
 * какие списки в этом API постраничные, а какие нет.
 */
@Schema(description = "Справочник языковых дивизионов")
public record DivisionCatalogResponseDTO(

        @Schema(description = "Дивизионы, упорядоченные по коду")
        List<DivisionView> items,

        @Schema(description = "Курсор следующей страницы; у закрытого справочника всегда null",
                example = "null", nullable = true)
        String nextCursor,

        @Schema(description = "Сколько записей могло приехать за раз", example = "9", type = "integer")
        int limit) {
}
