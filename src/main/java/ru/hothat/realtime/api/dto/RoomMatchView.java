package ru.hothat.realtime.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import ru.hothat.game.api.dto.ArsenalView;
import ru.hothat.game.api.dto.MatchStateView;
import ru.hothat.game.api.dto.PlayerAmmoView;
import ru.hothat.game.api.dto.ReplacementClipView;

import java.util.List;

/**
 * Партия в кадре канала — та же четвёрка полей, что у {@code GET /api/v2/game/{roomId}}.
 *
 * <p>Слово текущего хода видит только объясняющий: проекция собирается на
 * того, кто слушает, тем же сборщиком, что и ответ HTTP. Поэтому кадр,
 * улетающий отгадывающему и зрителю, приходит с пустым словом — и это
 * свойство сборки, а не договорённость с клиентом.
 *
 * <p>Боезапас разведён по охвату. {@code ammo} — счётчики каждого игрока за
 * столом: их рисует плитка каждого ({@code livekit.js:2483}), и до сих пор
 * ради них экран держал подписку на все строки {@code rooms/{id}/players}.
 * {@code arsenal} и {@code clips} — своё и только своё: какие мемы заряжены,
 * что отстреляно, когда кончится перезарядка, кого сняли для Подмены. Чужих
 * идентификаторов мемов в кадре нет ни в одном поле.
 */
@Schema(description = "Состояние партии в кадре канала")
public record RoomMatchView(

        @Schema(description = "Состояние партии глазами слушателя")
        MatchStateView match,

        @Schema(description = "Своё снаряжение; пусто у зрителя и у того, кто в партии не играет",
                nullable = true)
        ArsenalView arsenal,

        @Schema(description = "Счётчики боезапаса всех игроков комнаты — без содержимого обойм")
        List<PlayerAmmoView> ammo,

        @Schema(description = "Свои клипы Подмены: снятые и ждущие применения; пусто у зрителя")
        List<ReplacementClipView> clips) {
}
