package ru.hothat.game.domain;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Каталог арсенала: тринадцать видов оружия и всё, что про них известно.
 *
 * <p>Единственный источник правды. Базовый боезапас
 * ({@code GameRules.BASE_ARSENAL}) и список ключей ({@code ARSENAL_KEYS})
 * больше не объявляются руками, а выводятся отсюда — из-за их расхождения
 * {@code GameRules:34} падал с NPE, и выразить такое расхождение теперь нечем.
 *
 * <p>Тот же каталог отдаётся клиенту через {@code GET /api/v2/game/weapons},
 * поэтому копия во фронте ({@code app-core.js:356 BASE_ARSENAL}) становится
 * не нужна.
 */
public final class WeaponRegistry {

    /** Перезарядка между диверсиями: 3 секунды у всех, кроме помидора и пука. */
    public static final long COOLDOWN_MS = 3000;

    /**
     * Порог «эффект не поместится в остаток хода». Подмена и негатив длятся
     * десять секунд, апож — пятнадцать; секунда сверху — запас на дорогу.
     */
    private static final long ROOM_FOR_TEN_SECONDS = 11000;
    private static final long ROOM_FOR_FIFTEEN_SECONDS = 16000;

    private static final Map<WeaponType, Weapon> CATALOG = catalog();

    private WeaponRegistry() {
    }

    private static Map<WeaponType, Weapon> catalog() {
        Map<WeaponType, Weapon> weapons = new LinkedHashMap<>();
        // Мем: длительность у каждого ролика своя, поэтому приезжает из библиотеки.
        put(weapons, new Weapon(WeaponType.MEME, "meme", 4, Weapon.DURATION_FROM_MEDIA,
                EffectLock.NONE, false, false, false, 0, false, true, true));
        // Помидор — единственное оружие без перезарядки: им кидаются очередями.
        put(weapons, new Weapon(WeaponType.TOMATO, "tomato", 7, 2000,
                EffectLock.NONE, false, false, false, 0, true, true, true));
        put(weapons, new Weapon(WeaponType.CROCODILE, "crocodile", 2, Weapon.DURATION_TO_TURN_END,
                EffectLock.CROCODILE, false, false, false, 0, false, false, true));
        put(weapons, new Weapon(WeaponType.VOICE_BOGDAN, "voice", 4, 30000,
                EffectLock.VOICE, true, false, false, 0, false, false, true));
        put(weapons, new Weapon(WeaponType.VOICE_PROKURISH, "voice", 4, 30000,
                EffectLock.VOICE, true, false, false, 0, false, false, true));
        // Апож — свой ключ боезапаса: он выдаётся редкой наградой, а не за очки.
        put(weapons, new Weapon(WeaponType.VOICE_APOZH, "apozh", 1, 15000,
                EffectLock.VOICE, true, false, true, ROOM_FOR_FIFTEEN_SECONDS, false, false, true));
        put(weapons, new Weapon(WeaponType.NEGATIVE, "negative", 1, 10000,
                EffectLock.VIDEO, true, false, true, ROOM_FOR_TEN_SECONDS, false, false, true));
        // Подмена бьёт не по объясняющему, а по тому, кого сняли заранее.
        put(weapons, new Weapon(WeaponType.REPLACEMENT, "replacement", 1, 10000,
                EffectLock.REPLACEMENT, true, false, false, ROOM_FOR_TEN_SECONDS, false, false, false));
        // Маска Кита Пено — инструмент владельца сервиса: заряда у неё нет.
        put(weapons, new Weapon(WeaponType.MASK_KIT_PENOT, null, 0, 30000,
                EffectLock.VIDEO, true, true, false, 0, false, false, true));
        put(weapons, new Weapon(WeaponType.OBJECT, "object", 1, 10000,
                EffectLock.OVERLAY, true, false, false, 0, false, false, true));
        put(weapons, new Weapon(WeaponType.POOP, "poop", 1, 10000,
                EffectLock.OVERLAY, true, false, false, 0, false, false, true));
        // Ключ боезапаса megaText не совпадает с кодом mega_text: так его
        // назвал фронтенд, и переименование стоило бы миграции всех арсеналов.
        put(weapons, new Weapon(WeaponType.MEGA_TEXT, "megaText", 1, 15000,
                EffectLock.OVERLAY, true, false, false, 0, false, false, true));
        // Пук — шутка без эффекта, заряда и перезарядки.
        put(weapons, new Weapon(WeaponType.FART, null, 0, 0,
                EffectLock.NONE, true, false, false, 0, true, true, true));
        return Map.copyOf(weapons);
    }

    private static void put(Map<WeaponType, Weapon> weapons, Weapon weapon) {
        weapons.put(weapon.type(), weapon);
    }

    public static Weapon of(WeaponType type) {
        Weapon weapon = CATALOG.get(type);
        if (weapon == null) {
            throw new IllegalStateException("В каталоге нет оружия " + type);
        }
        return weapon;
    }

    public static List<Weapon> all() {
        return List.copyOf(CATALOG.values());
    }

    /**
     * Ключи боезапаса в порядке объявления оружия. Голосовые эффекты делят
     * один ключ, поэтому ключей десять, а оружия тринадцать.
     */
    public static List<String> ammoKeys() {
        LinkedHashSet<String> keys = new LinkedHashSet<>();
        for (Weapon weapon : CATALOG.values()) {
            if (weapon.consumesAmmo()) {
                keys.add(weapon.ammoKey());
            }
        }
        return List.copyOf(keys);
    }

    /** Стартовый боезапас партии — сумма базовых зарядов по каждому ключу. */
    public static Map<String, Integer> baseArsenal() {
        Map<String, Integer> arsenal = new LinkedHashMap<>();
        for (Weapon weapon : CATALOG.values()) {
            if (weapon.consumesAmmo()) {
                arsenal.putIfAbsent(weapon.ammoKey(), weapon.baseAmmo());
            }
        }
        return arsenal;
    }

    /**
     * Приведение арсенала к каноническому виду: недостающие ключи добираются из
     * базового набора, отрицательные значения обнуляются, лишние отбрасываются.
     * Ровно то же делал {@code GameRules.arsenal()}, но без падения на ключе,
     * которого нет в базовом наборе.
     */
    public static Map<String, Integer> normalizeArsenal(Map<String, Integer> stored) {
        Map<String, Integer> base = baseArsenal();
        Map<String, Integer> result = new LinkedHashMap<>();
        for (String key : ammoKeys()) {
            Integer amount = stored == null ? null : stored.get(key);
            result.put(key, Math.max(0, amount == null ? base.getOrDefault(key, 0) : amount));
        }
        return result;
    }

    /** Ключи редких наград: их выдаёт не счёт хода, а номер угаданного слова. */
    public static List<String> specialAmmoKeys() {
        return List.of("negative", "apozh", "replacement", "object", "poop", "megaText");
    }

    public static Optional<Weapon> find(String code) {
        return WeaponType.find(code).map(WeaponRegistry::of);
    }
}
