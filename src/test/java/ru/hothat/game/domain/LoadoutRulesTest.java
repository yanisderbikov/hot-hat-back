package ru.hothat.game.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Обойма мемов игрока: заряд перед партией, выдача наградой и замена слота. */
class LoadoutRulesTest {

    private static final List<String> FIVE = List.of("m1", "m2", "m3", "m4", "m5");

    private MatchPlayer armedPlayer() {
        MatchPlayer player = new MatchPlayer("uid-ann", "Аня", "team-red", false);
        player.setLoadout(new ArrayList<>(FIVE));
        LoadoutRules.arm(player);
        return player;
    }

    @Test
    @DisplayName("Обойма заряжена, когда в ней ровно пять разных мемов")
    void loadoutIsChargedAtFive() {
        assertThat(LoadoutRules.charged(FIVE)).isTrue();
        assertThat(LoadoutRules.charged(List.of("m1", "m2", "m3", "m4"))).isFalse();
        assertThat(LoadoutRules.charged(List.of("m1", "m1", "m2", "m3", "m4"))).isFalse();
    }

    @Test
    @DisplayName("К началу партии открыты столько мемов, сколько зарядов даёт стартовый боезапас")
    void armUnlocksAsManyMemesAsAmmo() {
        MatchPlayer player = armedPlayer();
        int memeAmmo = WeaponRegistry.baseArsenal().get("meme");

        assertThat(player.getAvailable()).hasSize(memeAmmo).containsExactly("m1", "m2", "m3", "m4");
        assertThat(player.getReserve()).containsExactly("m5");
        assertThat(player.getRecycle()).isEmpty();
        assertThat(player.getArsenal()).isEqualTo(WeaponRegistry.baseArsenal());
    }

    @Test
    @DisplayName("Награда открывает мем из резерва")
    void grantTakesFromReserve() {
        MatchPlayer player = armedPlayer();

        LoadoutRules.grant(player, 1);

        assertThat(player.getAvailable()).containsExactly("m1", "m2", "m3", "m4", "m5");
        assertThat(player.getReserve()).isEmpty();
    }

    @Test
    @DisplayName("Когда резерв кончился, награда возвращает использованный мем")
    void grantTakesFromRecycleWhenReserveIsEmpty() {
        MatchPlayer player = armedPlayer();
        player.setAvailable(new ArrayList<>(List.of("m2", "m3", "m4")));
        player.setReserve(new ArrayList<>());
        player.setRecycle(new ArrayList<>(List.of("m1", "m5")));

        LoadoutRules.grant(player, 1);

        assertThat(player.getAvailable()).containsExactly("m2", "m3", "m4", "m1");
        assertThat(player.getRecycle()).containsExactly("m5");
    }

    @Test
    @DisplayName("Награда в ноль мемов ничего не меняет")
    void zeroGrantChangesNothing() {
        MatchPlayer player = armedPlayer();

        LoadoutRules.grant(player, 0);

        assertThat(player.getAvailable()).containsExactly("m1", "m2", "m3", "m4");
        assertThat(player.getReserve()).containsExactly("m5");
    }

    @Test
    @DisplayName("Замена мема в слоте меняет его сразу во всех очередях")
    void replacingASlotUpdatesEveryQueue() {
        MatchPlayer player = armedPlayer();
        player.setUsedMemeIds(new ArrayList<>(List.of("m2")));

        LoadoutRules.replaceSlot(player, 1, "m9");

        assertThat(player.getLoadout()).containsExactly("m1", "m9", "m3", "m4", "m5");
        assertThat(player.getAvailable()).containsExactly("m1", "m9", "m3", "m4");
        assertThat(player.getUsedMemeIds()).containsExactly("m9");
        assertThat(player.getReserve()).containsExactly("m5");
    }

    @Test
    @DisplayName("Боезапас игрока не уходит в минус и не растёт из ничего")
    void ammoNeverGoesNegative() {
        MatchPlayer player = armedPlayer();

        player.spend("tomato", 100);
        assertThat(player.ammo("tomato")).isZero();

        player.grant("tomato", 2);
        assertThat(player.ammo("tomato")).isEqualTo(2);
        assertThat(player.ammo("нет такого")).isZero();
    }
}
