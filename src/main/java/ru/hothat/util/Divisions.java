package ru.hothat.util;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Перенос lib/divisions.js без изменений: коды, флаги, названия дивизионов. */
public final class Divisions {

    public record Division(String code, String flag, String locale, String name, String divisionName) {
    }

    public static final Map<String, Division> ALL;
    public static final Set<String> CODES;

    static {
        Map<String, Division> all = new LinkedHashMap<>();
        all.put("ru", new Division("ru", "🇷🇺", "ru-RU", "Русский", "Русский дивизион"));
        all.put("en", new Division("en", "🇬🇧", "en-GB", "English", "English Division"));
        all.put("de", new Division("de", "🇩🇪", "de-DE", "Deutsch", "Deutsche Division"));
        all.put("es", new Division("es", "🇪🇸", "es-ES", "Español", "División Española"));
        all.put("fr", new Division("fr", "🇫🇷", "fr-FR", "Français", "Division Française"));
        all.put("it", new Division("it", "🇮🇹", "it-IT", "Italiano", "Divisione Italiana"));
        all.put("zh", new Division("zh", "🇨🇳", "zh-CN", "中文", "中文赛区"));
        all.put("ja", new Division("ja", "🇯🇵", "ja-JP", "日本語", "日本語ディビジョン"));
        all.put("kk", new Division("kk", "🇰🇿", "kk-KZ", "Қазақша", "Қазақ дивизионы"));
        ALL = Map.copyOf(all);
        CODES = ALL.keySet();
    }

    private Divisions() {
    }

    public static String normalize(Object value) {
        return normalize(value, "ru");
    }

    public static String normalize(Object value, String fallback) {
        String code = value == null ? "" : String.valueOf(value).trim().toLowerCase();
        return CODES.contains(code) ? code : fallback;
    }

    public static Division meta(Object value) {
        return ALL.getOrDefault(normalize(value), ALL.get("ru"));
    }

    /** UI можно переключить только на свой дивизион или на английский. */
    public static String allowedUiLanguage(Object division, Object requested) {
        String div = normalize(division);
        String ui = normalize(requested, div);
        return ui.equals(div) || ui.equals("en") ? ui : div;
    }

    public static String badge(String divisionLanguage, boolean hasTeam) {
        Division d = meta(divisionLanguage);
        return d.flag() + " " + (hasTeam ? d.divisionName() : d.name());
    }

    public static String suggestedFromCountry(String country) {
        String c = country == null ? "" : country.trim().toUpperCase();
        if (c.isEmpty()) {
            return "en";
        }
        if (List.of("RU", "BY", "KG").contains(c)) return "ru";
        if (List.of("KZ").contains(c)) return "kk";
        if (List.of("DE", "AT", "CH", "LI").contains(c)) return "de";
        if (List.of("ES", "MX", "AR", "BO", "CL", "CO", "CR", "CU", "DO", "EC", "SV", "GT", "HN", "NI", "PA", "PY",
                "PE", "PR", "UY", "VE").contains(c)) return "es";
        if (List.of("FR", "BE", "MC", "LU", "HT", "SN", "CI", "CM", "MG", "ML", "NE", "BF", "TG", "BJ", "GA", "CG",
                "CD").contains(c)) return "fr";
        if (List.of("IT", "SM", "VA").contains(c)) return "it";
        if (List.of("CN", "HK", "MO", "TW").contains(c)) return "zh";
        if (List.of("JP").contains(c)) return "ja";
        return "en";
    }
}
