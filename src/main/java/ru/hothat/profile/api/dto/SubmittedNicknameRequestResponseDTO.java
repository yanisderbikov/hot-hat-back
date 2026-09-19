package ru.hothat.profile.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Принятая заявка на ник.
 *
 * <p>Старый адрес отвечал одним {@code ok:true}, и экран профиля показывал
 * «Запрос отправлен», ничем это не подтверждая. Здесь возвращается пара
 * «было — просим»: ровно то, что уехало владельцу сервиса, и ровно то,
 * что нужно показать в списке своих заявок.
 */
@Schema(description = "Заявка на ник, принятая к разбору")
public record SubmittedNicknameRequestResponseDTO(

        @Schema(description = "Ник, который у игрока сейчас", example = "player4f2a")
        String currentNickname,

        @Schema(description = "Ник, о котором просит игрок", example = "Fermer")
        String requestedNickname) {
}
