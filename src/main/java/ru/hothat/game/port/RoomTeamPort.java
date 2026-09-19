package ru.hothat.game.port;

import java.util.List;

/**
 * Команды комнаты: их состав и счёт.
 *
 * <p>Счёт — единственное, что партия в командах меняет, и меняет его только
 * так: прибавкой. Читать-править-записывать здесь нельзя — два угаданных слова
 * в двух командах приходят одновременно, и последняя запись затёрла бы первую
 * (замечание B2 аудита о потерянном обновлении).
 */
public interface RoomTeamPort {

    List<RoomTeamView> teams(String roomId);

    /** Прибавка к счёту команды одним действием базы. */
    void addScore(String roomId, String teamId, int delta);

    /** Замороженный на старте состав команды и обнулённый счёт. */
    void freezeRoster(String roomId, String teamId, List<String> memberUids);

    record RoomTeamView(String teamId, String name, int order, int score, List<String> memberUids) {
    }
}
