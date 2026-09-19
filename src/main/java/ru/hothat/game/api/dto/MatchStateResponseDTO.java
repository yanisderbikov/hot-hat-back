package ru.hothat.game.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * Снимок партии.
 *
 * <p>Заменяет чтение игровых полей комнаты документом и подпиской. Слово
 * текущего хода видит только объясняющий — остальным поле приходит пустым,
 * включая зрителей и того, кто угадывает.
 *
 * <p>Боезапас приходит двумя полями с разным охватом: {@code ammo} — счётчики
 * всех игроков, они рисуются на плитках у каждого; {@code arsenal} и
 * {@code clips} — только своё содержимое. Ту же четвёрку и в том же порядке
 * несёт кадр канала {@code /ws/v2/room/{roomId}}: экран кормит одного
 * отрисовщика обоими источниками.
 */
@Schema(description = "Снимок партии")
public record MatchStateResponseDTO(

        @Schema(description = "Состояние партии")
        MatchStateView match,

        @Schema(description = "Своё снаряжение; пусто у зрителя и у того, кто в партии не играет",
                nullable = true)
        ArsenalView arsenal,

        @Schema(description = "Счётчики боезапаса всех игроков комнаты — без содержимого обойм")
        List<PlayerAmmoView> ammo,

        @Schema(description = "Свои клипы Подмены: снятые и ждущие применения; пусто у зрителя")
        List<ReplacementClipView> clips) {
}
