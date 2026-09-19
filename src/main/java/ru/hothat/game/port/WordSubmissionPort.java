package ru.hothat.game.port;

import java.util.List;

/**
 * Слова, сданные игроками в шляпу до начала партии.
 *
 * <p>Чужие слова не читает никто, включая хозяина комнаты: увидеть их — значит
 * узнать половину партии заранее. Наружу отдаётся только своя пачка и общий
 * счётчик; целиком шляпу собирает старт партии, и там она сразу перемешивается.
 */
public interface WordSubmissionPort {

    /** Своя пачка слов. */
    List<String> read(String roomId, String uid);

    /** Записать свою пачку и вернуть её же, как её сохранил сервер. */
    SubmissionView write(String roomId, String uid, List<String> words, boolean testBot);

    /** Все слова комнаты — только для старта партии. */
    List<String> collect(String roomId, int limit);

    /** Сколько слов в шляпе всего. */
    int total(String roomId);

    record SubmissionView(List<String> words, int mine, int total) {
    }
}
