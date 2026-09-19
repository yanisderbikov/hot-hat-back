package ru.hothat.game.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.Map;

/**
 * Боезапас одного игрока так, как его видит вся комната.
 *
 * <p>Счётчики — публичные, содержимое — нет. Плитка каждого игрока рисует
 * четыре суммы зарядов ({@code livekit.js:2483}): сколько у соседа
 * помидоров, видно всем за столом, и это часть игры — как фишки перед
 * игроком в покере. А вот <i>какие</i> мемы он зарядил, что из них уже
 * отстреляно, когда кончится его перезарядка и кого он снял для Подмены —
 * половина тактики, и она остаётся в {@link ArsenalView}, который каждый
 * получает только про себя.
 *
 * <p>{@code loadoutCharged} — тоже счёт, а не содержимое: экран настройки не
 * даёт начать партию, пока у кого-то из состава обойма короче пяти
 * ({@code app-core.js:5152}, {@code :11995}), и до сих пор ради этого факта
 * каждому приезжали чужие идентификаторы мемов.
 *
 * <p>Карта зарядов — в той же форме, что {@link ArsenalView#ammo()} и базовый
 * набор из каталога оружия: плитка рисует их одним кодом.
 */
@Schema(description = "Боезапас игрока без содержимого обоймы")
public record PlayerAmmoView(

        @Schema(description = "Чей боезапас", example = "pL9Mn2bV3cX4zA5sD6fG7hJ8kL9m")
        String uid,

        @Schema(description = "Заряды по видам оружия; ключи те же, что у своего снаряжения",
                example = "{\"meme\":4,\"tomato\":7,\"crocodile\":2,\"voice\":4,\"negative\":1,\"apozh\":1,"
                        + "\"replacement\":1,\"object\":1,\"poop\":1,\"megaText\":1}")
        Map<String, Integer> ammo,

        @Schema(description = "Заряжены ли все пять слотов обоймы: без этого партия не начнётся",
                example = "true", type = "boolean")
        boolean loadoutCharged) {
}
