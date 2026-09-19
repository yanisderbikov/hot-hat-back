package ru.hothat.realtime.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Переключить превью внутри канала лобби.
 *
 * <p>Наследник трёх подписок на состав выбранной комнаты
 * ({@code home/home.js:90}) и подписки на документ игрока
 * ({@code live-preview.js:34}) — но без нового рукопожатия. Главная крутит
 * показываемую комнату каждые тридцать секунд у каждого посетителя, включая
 * гостя: отдельный канал превью означал бы разрыв соединения и проверку токена
 * дважды в минуту.
 *
 * <p>Подписка одна: новое значение заменяет прежнее, и второго превью в одном
 * сокете не бывает. Снять её, не закрывая канал, — кадр
 * {@link ChannelUnsubscribeFrameDTO} с {@code id=spotlight}.
 */
@Schema(description = "Выбрать комнату для превью")
public record SpotlightFrameDTO(

        @Schema(description = "Имя кадра", example = "spotlight", allowableValues = "spotlight")
        String type,

        @Schema(description = "Комната, которую показывать крупным планом",
                example = "hat-0f3a9c1d7b2e5480", pattern = "^hat-[a-f0-9]{16}$")
        String roomId) {
}
