package ru.hothat.game.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Награды за ход: обычные — за счёт, редкие — за номер угаданного слова. */
class RewardRulesTest {

    @Test
    @DisplayName("За ход без угаданных слов не даётся ничего")
    void emptyTurnGivesNothing() {
        assertThat(RewardRules.forScore(0))
                .containsEntry("tomato", 0).containsEntry("meme", 0)
                .containsEntry("voice", 0).containsEntry("crocodile", 0);
    }

    @Test
    @DisplayName("Помидоры и мемы идут парами: два помидора за каждые два слова, два мема за каждые три")
    void tomatoesEveryTwoWordsAndMemesEveryThree() {
        assertThat(RewardRules.forScore(1)).containsEntry("tomato", 0).containsEntry("meme", 0);
        assertThat(RewardRules.forScore(2)).containsEntry("tomato", 2).containsEntry("meme", 0);
        assertThat(RewardRules.forScore(3)).containsEntry("tomato", 2).containsEntry("meme", 2);
        assertThat(RewardRules.forScore(4)).containsEntry("tomato", 4).containsEntry("meme", 2);
        assertThat(RewardRules.forScore(6)).containsEntry("tomato", 6).containsEntry("meme", 4);
    }

    @Test
    @DisplayName("Голосовой эффект выдаётся вместе с мемом, крокодил — за каждые пять слов")
    void voiceFollowsMemeAndCrocodileEveryFive() {
        assertThat(RewardRules.forScore(5))
                .containsEntry("meme", 2).containsEntry("voice", 2).containsEntry("crocodile", 1);
        assertThat(RewardRules.forScore(9)).containsEntry("crocodile", 1);
        assertThat(RewardRules.forScore(10)).containsEntry("crocodile", 2);
    }

    @Test
    @DisplayName("Отрицательный счёт наград не приносит и в минус не уходит")
    void negativeScoreGivesNothing() {
        assertThat(RewardRules.forScore(-3))
                .containsEntry("tomato", 0).containsEntry("meme", 0)
                .containsEntry("voice", 0).containsEntry("crocodile", 0);
    }

    @Test
    @DisplayName("Редкие награды идут по сквозному счёту команды: до четвёртого слова их нет")
    void specialsStartAtFourthWord() {
        assertThat(RewardRules.specialsBetween(0, 3)).isEmpty();
        assertThat(RewardRules.specialsBetween(3, 4)).containsExactly("negative");
        assertThat(RewardRules.specialsBetween(4, 6)).containsExactly("apozh");
        assertThat(RewardRules.specialsBetween(12, 13)).containsExactly("megaText");
        assertThat(RewardRules.specialsBetween(10, 11)).containsExactly("poop");
    }

    @Test
    @DisplayName("За один ход выдаются все редкие награды, чьи номера ход перешагнул")
    void allSpecialsInRangeAreGranted() {
        assertThat(RewardRules.specialsBetween(0, 13)).containsExactly(
                "negative", "apozh", "negative", "replacement", "object", "poop",
                "negative", "apozh", "megaText");
    }

    @Test
    @DisplayName("Счёт команды не может уменьшиться: назад редкие награды не выдаются")
    void scoreCannotWalkBack() {
        assertThat(RewardRules.specialsBetween(5, 3)).isEmpty();
    }

    @Test
    @DisplayName("Редкие награды раздаются по кругу, а не копятся у первого игрока состава")
    void specialsRotateInsideTeam() {
        RewardRules.SpecialGrant grant = RewardRules.distribute(
                List.of("negative", "apozh", "object"), List.of("uid-ann", "uid-bob"), 0);

        assertThat(grant.byUid().get("uid-ann")).containsEntry("negative", 1).containsEntry("object", 1);
        assertThat(grant.byUid().get("uid-bob")).containsEntry("apozh", 1);
        assertThat(grant.nextCursor()).isEqualTo(3);
    }

    @Test
    @DisplayName("Курсор команды продолжается со следующего хода, а не начинается заново")
    void cursorContinuesAcrossTurns() {
        RewardRules.SpecialGrant grant = RewardRules.distribute(
                List.of("negative"), List.of("uid-ann", "uid-bob"), 3);

        assertThat(grant.byUid()).containsOnlyKeys("uid-bob");
        assertThat(grant.nextCursor()).isEqualTo(4);
    }

    @Test
    @DisplayName("Получателю едет полный набор ключей с нулями: клиент рисует прибавку по каждому")
    void recipientGetsEveryKeyWithZeroes() {
        RewardRules.SpecialGrant grant = RewardRules.distribute(
                List.of("poop"), List.of("uid-ann"), 0);

        assertThat(grant.byUid().get("uid-ann"))
                .containsOnlyKeys(WeaponRegistry.specialAmmoKeys().toArray(String[]::new))
                .containsEntry("poop", 1).containsEntry("negative", 0);
    }

    @Test
    @DisplayName("Раздавать награды некому: пустой состав не двигает курсор")
    void emptyRosterKeepsCursor() {
        RewardRules.SpecialGrant grant = RewardRules.distribute(List.of("negative"), List.of(), 7);

        assertThat(grant.byUid()).isEmpty();
        assertThat(grant.nextCursor()).isEqualTo(7);
    }
}
