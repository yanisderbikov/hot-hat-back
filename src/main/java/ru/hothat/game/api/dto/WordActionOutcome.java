package ru.hothat.game.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** Чем кончился разбор слова. */
@Schema(description = "Исход действия со словом")
public enum WordActionOutcome {

    /** Слово засчитано команде. */
    COUNTED,
    /** Слово возвращено в шляпу. */
    SKIPPED,
    /** Это же слово уже разобрано этим же запросом: повтор ничего не изменил. */
    REPEATED,
    /** Ход истёк раньше нажатия: слово не засчитано, ход закрыт. */
    TURN_EXPIRED
}
