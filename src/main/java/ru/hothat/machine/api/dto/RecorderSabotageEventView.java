package ru.hothat.machine.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Диверсия, которую рекордер рисует поверх кадра.
 *
 * <p>Событие приходит из свободной jsonb-структуры комнаты, поэтому его
 * просеивают: чужой вид отбрасывается целиком, ссылки на медиа принимаются
 * только по https, длительность зажата двадцатью секундами. Просеивание
 * было и раньше ({@code RecorderStateServiceImpl.safeSabotageEvent}), но его
 * результат уезжал картой без схемы.
 */
@Schema(description = "Диверсия в кадре записи")
public record RecorderSabotageEventView(

        @Schema(description = "Идентификатор события; по нему страница не проигрывает эффект дважды",
                example = "sab-9f2c1a")
        String id,

        @Schema(description = "Вид диверсии")
        RecorderSabotageType type,

        @Schema(description = "Кто атаковал", example = "Qk3xZaTb9mNpR2sVuWyA1cEfGhJk")
        String attackerUid,

        @Schema(description = "Имя атакующего", example = "Вася")
        String attackerName,

        @Schema(description = "По кому прилетело; пусто, если диверсия без цели",
                example = "Rp8mQzXc2vNbT4sKuWyA1cEfGhJk", nullable = true)
        String targetUid,

        @Schema(description = "Когда событие создано, мс эпохи",
                example = "1757068800000", type = "integer", format = "int64")
        long createdAtMs,

        @Schema(description = "Номер партии, к которой относится событие", example = "3", type = "integer")
        int gameNumber,

        @Schema(description = "Сколько играть эффект, мс; не больше 20000",
                example = "4000", type = "integer", format = "int64")
        long durationMs,

        @Schema(description = "Идентификатор мема; только у диверсии вида meme",
                example = "meme-7Kd3xQ", nullable = true)
        String memeId,

        @Schema(description = "Название мема; только у диверсии вида meme",
                example = "Другой не знаю", nullable = true)
        String memeTitle,

        @Schema(description = "Ссылка на ролик мема; только https и только у вида meme",
                example = "https://media.hot-hat.ru/memes/7Kd3xQ.mp4", nullable = true)
        String memeSrc,

        @Schema(description = "Ссылка на постер мема; только https и только у вида meme",
                example = "https://media.hot-hat.ru/memes/7Kd3xQ.jpg", nullable = true)
        String memePoster) {
}
