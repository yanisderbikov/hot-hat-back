package ru.hothat.room.domain;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Замороженный состав партии: кто в ней играет и за какую команду.
 *
 * <p>Переезд {@code GameRules.rosterForTeam} и {@code GameRules.allGamePlayers}
 * из общей статики партии (§7.5 плана). Состав принадлежит комнате: это по
 * нему решают, впустить ли вернувшегося посреди партии и чьё место можно
 * освободить. Партия читает то же самое через порт, а не своей копией — иначе
 * «участник этой партии» имел бы два ответа.
 *
 * <p>Правило принимает карту составов, а не строку комнаты: тогда «правило
 * знает про сущность соседа» не компилируется, а проверить его можно без
 * поднятого приложения.
 *
 * <p>Составы заморожены на всю партию намеренно. Они и есть ответ на вопрос,
 * который в старом коде задавали строке игрока: у неё поле {@code teamId}
 * может обнулиться от чужой правки или не восстановиться после возврата на
 * iPad, и тогда человек оказывался вне собственной команды. Здесь источник
 * правды один.
 */
public final class RosterRules {

    private RosterRules() {
    }

    /** Все игроки партии — по составам команд, а не по списку мест в комнате. */
    public static List<String> allPlayers(Map<String, List<String>> rostersByTeam) {
        List<String> all = new ArrayList<>();
        for (List<String> roster : rostersByTeam.values()) {
            for (String uid : roster) {
                if (uid != null && !uid.isBlank() && !all.contains(uid)) {
                    all.add(uid);
                }
            }
        }
        return all;
    }

    /** Состав одной команды без повторов. */
    public static List<String> teamRoster(Map<String, List<String>> rostersByTeam, String teamId) {
        List<String> roster = new ArrayList<>();
        for (String uid : rostersByTeam.getOrDefault(teamId, List.of())) {
            if (uid != null && !uid.isBlank() && !roster.contains(uid)) {
                roster.add(uid);
            }
        }
        return roster;
    }

    /**
     * За какую команду человек играет в этой партии.
     *
     * <p>Пусто — он в партии не участвует. Это и есть проверка «пускать ли
     * внутрь начавшейся игры»: новых игроков партия не принимает, а своих
     * возвращает на их прежнее место.
     */
    public static String teamOf(Map<String, List<String>> rostersByTeam, String uid) {
        if (uid == null || uid.isBlank()) {
            return null;
        }
        for (Map.Entry<String, List<String>> entry : rostersByTeam.entrySet()) {
            if (entry.getValue() != null && entry.getValue().contains(uid)) {
                return entry.getKey();
            }
        }
        return null;
    }

    /**
     * Составы без ушедшего.
     *
     * <p>Нужно выходу из партии: место, за которым никого нет, продолжало бы
     * получать ходы. Карта возвращается новая — исходную никто не портит.
     */
    public static Map<String, List<String>> without(Map<String, List<String>> rostersByTeam, String uid) {
        Map<String, List<String>> next = new LinkedHashMap<>();
        rostersByTeam.forEach((teamId, roster) -> {
            List<String> kept = new ArrayList<>();
            for (String member : roster == null ? List.<String>of() : roster) {
                if (!member.equals(uid)) {
                    kept.add(member);
                }
            }
            next.put(teamId, kept);
        });
        return next;
    }
}
