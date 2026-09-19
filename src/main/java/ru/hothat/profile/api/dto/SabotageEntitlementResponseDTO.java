package ru.hothat.profile.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Сколько партий в режиме диверсий игроку ещё доступно.
 *
 * <p>Сегодня это вложенный блок {@code sabotage} внутри {@code ensure_profile}
 * ({@code ProfileServiceImpl:353-375}) — квота в ответе о нике и аватаре.
 * Здесь у неё свой адрес: правило «пять бесплатных партий, у владельца и его
 * друзей безлимит» не имеет отношения к карточке игрока и меняется отдельно
 * от неё.
 *
 * <p>Сама квота лежит вложенной проекцией, а не тремя полями ответа: ту же
 * проекцию возвращает списание партии, и общая запись гарантирует, что оба
 * адреса описывают квоту одними и теми же словами.
 */
@Schema(description = "Квота диверсий текущего игрока")
public record SabotageEntitlementResponseDTO(

        @Schema(description = "Состояние квоты")
        SabotageEntitlementView entitlement) {
}
