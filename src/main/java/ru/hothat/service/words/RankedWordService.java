package ru.hothat.service.words;

import java.util.List;

/** Генератор наборов слов для рейтинговой игры (порт lib/ranked-words.js). */
public interface RankedWordService {

    /** Нормализация для сравнения с историей игрока: локаль дивизиона, ё→е для русского. */
    String normalize(String value, String language);

    /**
     * @param count           сколько слов нужно (10 на игрока)
     * @param playerHistories уже выданные каждому игроку слова
     * @param language        дивизион, из пула которого берутся слова
     */
    List<String> generate(int count, List<List<String>> playerHistories, String language);
}
