package ru.hothat.game.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Optional;

/**
 * Вид диверсионного оружия — закрытый набор из тринадцати значений.
 *
 * <p>Ради этого перечисления и затевался единственный адрес диверсий вместо
 * тринадцати: раньше вид оружия был свободной строкой в теле запроса, и
 * список допустимых значений жил в трёх разошедшихся копиях — в
 * {@code ALLOWED_SABOTAGE} на сервере, в {@code BASE_ARSENAL} там же и в
 * своей копии во фронте ({@code app-core.js:356}). Из-за расхождения
 * {@code GameRules.arsenal()} падал с NPE на ключе, которого нет в базовом
 * наборе. Теперь неизвестное значение отвергает Jackson до входа в
 * контроллер, а сам набор выводится из {@link WeaponRegistry}.
 *
 * <p>{@link #code()} — строка, которую шлёт и рисует фронтенд; менять её
 * нельзя, переезд движка на сервер не должен ломать клиент.
 */
public enum WeaponType {

    MEME("meme"),
    TOMATO("tomato"),
    CROCODILE("crocodile"),
    VOICE_BOGDAN("voice_bogdan"),
    VOICE_PROKURISH("voice_prokurish"),
    VOICE_APOZH("voice_apozh"),
    NEGATIVE("negative"),
    REPLACEMENT("replacement"),
    MASK_KIT_PENOT("mask_kit_penot"),
    OBJECT("object"),
    POOP("poop"),
    MEGA_TEXT("mega_text"),
    FART("fart");

    private final String code;

    WeaponType(String code) {
        this.code = code;
    }

    @JsonValue
    public String code() {
        return code;
    }

    @JsonCreator
    public static WeaponType of(String value) {
        return find(value).orElseThrow(() -> new IllegalArgumentException("WEAPON_INVALID"));
    }

    public static Optional<WeaponType> find(String value) {
        String normalized = value == null ? "" : value.trim();
        for (WeaponType type : values()) {
            if (type.code.equals(normalized)) {
                return Optional.of(type);
            }
        }
        return Optional.empty();
    }
}
