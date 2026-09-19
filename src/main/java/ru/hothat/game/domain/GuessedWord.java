package ru.hothat.game.domain;

/**
 * Слово, разобранное в ходе: угаданное или пропущенное.
 *
 * <p>{@code id} придумывает клиент и присылает в адресе операции. Это и есть
 * ключ идемпотентности: повтор запроса с тем же {@code id} находит слово уже
 * записанным и не начисляет очко второй раз — ровно то, чего требует риск 2
 * плана. Он же адресует слово в голосовании апелляции, поэтому обязан
 * пережить ход.
 *
 * <p>Пропущенные слова лежат в том же списке с {@code skipped = true}. Иначе
 * повтор «Пропустить» после потери связи было бы нечем узнать: у пропуска нет
 * ни очка, ни следа в счёте, и второй такой запрос молча прокрутил бы шляпу
 * ещё на одно слово. В апелляцию и в счёт они не идут.
 */
public record GuessedWord(String id, String word, boolean skipped) {

    public static GuessedWord guessed(String id, String word) {
        return new GuessedWord(id, word, false);
    }

    public static GuessedWord skipped(String id, String word) {
        return new GuessedWord(id, word, true);
    }

    public boolean counted() {
        return !skipped;
    }
}
