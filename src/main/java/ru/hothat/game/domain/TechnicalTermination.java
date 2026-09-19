package ru.hothat.game.domain;

import java.util.List;

/**
 * Техническое завершение партии: доигрывать некому.
 *
 * <p>{@code noPenalty} отделяет «двое отвалились» от «у всех упал интернет».
 * В первом случае рейтинг считается, во втором партия аннулируется целиком —
 * наказывать за общий обрыв связи некого.
 */
public record TechnicalTermination(String type,
                                   List<String> missingUids,
                                   List<String> missingNames,
                                   boolean noPenalty,
                                   long endedAtMs) {

    public static final String RANKED = "ranked_disconnect";
    public static final String CASUAL = "casual_disconnect";
}
