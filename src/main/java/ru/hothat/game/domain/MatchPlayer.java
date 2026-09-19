package ru.hothat.game.domain;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Игрок партии глазами движка: боезапас, обойма мемов и перезарядка.
 *
 * <p>Строки {@code room_player} движок не видит — они приезжают сюда и
 * уезжают обратно в {@code store}. Правило «активная команда не меняет мемы»
 * не должно уметь читать колонку аватара.
 */
@Getter
@Setter
public final class MatchPlayer {

    private final String uid;
    private String name;
    private String teamId;
    private final boolean testBot;

    /** Боезапас по ключам арсенала; приведён к каноническому виду при загрузке. */
    private Map<String, Integer> arsenal = new LinkedHashMap<>();
    /** Пять заряженных мемов — их выбирает игрок до партии. */
    private List<String> loadout = new ArrayList<>();
    /** Мемы, готовые к выстрелу прямо сейчас. */
    private List<String> available = new ArrayList<>();
    /** Мемы, ждущие следующей награды за три угаданных слова. */
    private List<String> reserve = new ArrayList<>();
    /** Уже использованные: вернутся в оборот, когда кончится резерв. */
    private List<String> recycle = new ArrayList<>();
    private List<String> usedMemeIds = new ArrayList<>();
    private int cycleCursor;

    private long sabotageCooldownUntil;
    private long lastSeenAt;

    public MatchPlayer(String uid, String name, String teamId, boolean testBot) {
        this.uid = uid;
        this.name = name;
        this.teamId = teamId;
        this.testBot = testBot;
    }

    public int ammo(String key) {
        Integer amount = arsenal.get(key);
        return amount == null ? 0 : Math.max(0, amount);
    }

    public void spend(String key, int amount) {
        arsenal.put(key, Math.max(0, ammo(key) - Math.max(0, amount)));
    }

    public void grant(String key, int amount) {
        arsenal.put(key, ammo(key) + Math.max(0, amount));
    }

    public String displayName() {
        return name == null || name.isBlank() ? "Игрок" : name;
    }
}
