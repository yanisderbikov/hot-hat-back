package ru.hothat.game.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.Map;

/**
 * Награды команды за ход.
 *
 * <p>Редкое оружие названо отдельно и поимённо: оно выдаётся не за счёт хода, а
 * по номеру угаданного командой слова за партию, и достаётся одному из двоих
 * по очереди. Клиент показывает «Борису достался Апож», и без разбивки по
 * игрокам эту фразу собрать не из чего.
 */
@Schema(description = "Начисленные за ход награды")
public record MatchRewardsView(

        @Schema(description = "Обычные награды за счёт хода",
                example = "{\"tomato\":4,\"meme\":2,\"voice\":2,\"crocodile\":0}")
        Map<String, Integer> byScore,

        @Schema(description = "Редкие награды по игрокам",
                example = "{\"pL9Mn2bV3cX4zA5sD6fG7hJ8kL9m\":{\"negative\":1,\"apozh\":0,\"replacement\":0,"
                        + "\"object\":0,\"poop\":0,\"megaText\":0}}")
        Map<String, Map<String, Integer>> specialByUid) {
}
