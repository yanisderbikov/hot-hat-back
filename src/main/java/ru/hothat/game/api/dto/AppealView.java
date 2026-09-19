package ru.hothat.game.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * Голосование по спорным словам.
 *
 * <p>{@code eligibleCount} и {@code majority} едут вместе со словами: без них
 * клиент не соберёт строку «за отмену 2 из 3» и не поймёт, решено ли уже дело.
 *
 * <p>{@code turnId} здесь потому, что без него голосование некому подвести.
 * Разбираемый ход уже закрыт, и {@code MatchStateView.turn} на время апелляции
 * пуст — а {@code POST /appeal/closing} и {@code POST /turn/next} требуют
 * идентификатор хода. Держать его только в памяти того браузера, который ход
 * закрывал, нельзя: перезагрузка страницы, переподключение или подведение
 * итога другим участником оставляли партию в фазе {@code appeal} навсегда,
 * потому что назвать ход было уже нечем. Ровно то же соображение, по которому
 * идентификатор хода едет рекордеру в {@code RecorderRoomStateView}.
 */
@Schema(description = "Апелляция по закрытому ходу")
public record AppealView(

        @Schema(description = "Ход, по которому идёт голосование: его называют при подведении итога",
                example = "turn_3f9a1c04b77e2d15")
        String turnId,

        @Schema(description = "Когда голосование закроется, миллисекунды эпохи", example = "1757150410000")
        long endsAtMs,

        @Schema(description = "Сколько человек имеет право голоса: все, кроме разбираемой команды", example = "6")
        int eligibleCount,

        @Schema(description = "Сколько голосов отменяет слово", example = "4")
        int majority,

        @Schema(description = "Слова хода с текущим счётом голосов")
        List<AppealWordView> words) {

    public static AppealView closed() {
        return new AppealView(null, 0, 0, 0, List.of());
    }
}
