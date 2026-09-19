package ru.hothat.game.port;

import java.util.List;

/**
 * Слова для рейтинговой партии.
 *
 * <p>В рейтинге слова не приносят игроки: их выдаёт сервер из пула дивизиона,
 * оглядываясь на то, что этим людям уже выпадало. Иначе пара, играющая каждый
 * день, встречала бы один и тот же «Абажур» и заранее знала бы половину шляпы.
 */
public interface RankedWordPort {

    /**
     * Набор слов для перечисленных игроков.
     *
     * @param language дивизион, из пула которого берутся слова
     */
    List<String> generate(int count, List<String> playerUids, String language);

    /**
     * Запомнить выданное каждому игроку.
     *
     * <p>Отдельным вызовом, а не внутри выдачи: набор может не подойти и
     * партия не начаться, и тогда запоминать нечего.
     */
    void remember(List<String> playerUids, List<String> words, String language);
}
