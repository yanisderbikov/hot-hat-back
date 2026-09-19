package ru.hothat.game.domain;

import java.util.List;

/**
 * Состояние голосования после поданного голоса.
 *
 * <p>В ответ едет весь расклад, а не записанное значение: по нему клиент
 * рисует «за отмену 2 из 3», и без числа имеющих право эту строку было бы
 * не собрать.
 */
public record AppealVoteCast(List<String> myVotes, int eligibleCount, int votesForWord, int majority) {
}
