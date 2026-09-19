package ru.hothat.game.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Случившаяся диверсия.
 *
 * <p>Событие самодостаточно: ссылки на ролик, координаты накладки и
 * длительность едут в нём же. Сцена рисует его, ничего не дочитывая, —
 * то же самое событие приходит и по каналу данных видеосвязи.
 *
 * <p>Идентификатор зовётся {@code eventId}, как у всех записей области
 * ({@code clipId}, {@code turnId}, {@code wordId}); пакет видеосвязи и прежний
 * документ комнаты зовут его {@code id}, и экран сводит имена на границе
 * ({@code normalizeSabotageEvent}, {@code app-core.js:10228}) — это относится
 * и к каждому элементу {@code sabotageEventsRecent}, не только к
 * {@code lastSabotage}.
 *
 * <p>Событие съёмки Подмены ({@code replacement_record}) в состояние партии
 * и кадр канала попадает только к снимающему и снимаемому: оно несёт номер
 * клипа и цель, а это данные, которые сборщик проекций от чужих глаз прячет.
 */
@Schema(description = "Диверсия на сцене")
public record SabotageEventView(

        @Schema(description = "Идентификатор события", example = "sab_0a1b2c3d4e5f60718293")
        String eventId,

        @Schema(description = "Вид оружия", example = "tomato")
        String type,

        @Schema(description = "Кто атаковал", example = "pL9Mn2bV3cX4zA5sD6fG7hJ8kL9m")
        String attackerUid,

        @Schema(description = "Имя атакующего", example = "Борис")
        String attackerName,

        @Schema(description = "По кому пришлось: обычно объясняющий, у Подмены — снятый игрок",
                example = "kZ8Qw1nBv2mX3cL4aS5dF6gH7jK8", nullable = true)
        String targetUid,

        @Schema(description = "Когда применено, миллисекунды эпохи", example = "1757150380000")
        long createdAtMs,

        @Schema(description = "Сколько миллисекунд держится эффект", example = "2000")
        long durationMs,

        @Schema(description = "Номер партии: событие прошлой партии сцена не доигрывает", example = "3")
        int gameNumber,

        @Schema(description = "Мем, если стреляли мемом", example = "meme-7b1c2d3e4f5a", nullable = true)
        String memeId,

        @Schema(description = "Заголовок мема", example = "BMW — другой не знаю", nullable = true)
        String memeTitle,

        @Schema(description = "Ссылка на ролик", example = "https://cdn.hot-hat.ru/memes/bmw.mp4", nullable = true)
        String memeSrc,

        @Schema(description = "Ссылка на обложку ролика", example = "https://cdn.hot-hat.ru/memes/bmw.webp",
                nullable = true)
        String memePoster,

        @Schema(description = "Путь ролика в хранилище", example = "memes/7b1c2d3e4f5a.mp4", nullable = true)
        String memeMediaPath,

        @Schema(description = "Путь обложки в хранилище", example = "memes/7b1c2d3e4f5a.webp", nullable = true)
        String memePosterPath,

        @Schema(description = "Чем раздаётся файл ролика", example = "s3", nullable = true)
        String memeStorageProvider,

        @Schema(description = "Клип Подмены", example = "repl_9f8e7d6c5b4a39281706", nullable = true)
        String clipId,

        @Schema(description = "Ход, в котором снят клип Подмены", example = "turn_3f9a1c04b77e2d15",
                nullable = true)
        String recordedTurnId,

        @Schema(description = "Доля ширины сцены для накладки", example = "0.42", nullable = true)
        Double x,

        @Schema(description = "Доля высоты сцены для накладки", example = "0.61", nullable = true)
        Double y,

        @Schema(description = "Реплика «мысли-облака»: только у облака ботов прежнего движка, "
                + "у игроков облако летит пакетом видеосвязи", example = "Кажется, это слон", nullable = true)
        String text,

        @Schema(description = "Голос озвучки реплики облака, вместе с text", example = "2", nullable = true)
        String voiceId) {
}
