package ru.hothat.auth.spi;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Что область {@code auth} отвечает соседям об учётной записи.
 *
 * <p>Здесь только проекции: род учётки, почта, поколение токенов и права.
 * Спрашивают их разбор токена, реестр владельца и дашборд администратора —
 * своих копий этих полей у них больше нет.
 *
 * <p>Порт, а не общий репозиторий: чужая область не получает доступа к
 * {@code v2.user_account} и не может ни прочитать хеш пароля, ни завести
 * второго хозяина у поколения токенов.
 */
public interface AccountPort {

    /** Учётка игрока; пусто — такой нет. */
    Optional<Account> account(String uid);

    /** Учётки многих игроков разом: страница реестра — один запрос. */
    Map<String, Account> accounts(Collection<String> uids);

    /**
     * Полноценные учётки, новые сверху: реестр владельца сервиса.
     * Гостей здесь нет — у них нет ни почты, ни даты регистрации.
     */
    List<Account> members(int limit);

    /**
     * Идентификатор владельца сервиса; пусто — он ещё не заводил аккаунт.
     *
     * <p>Спрашивают его квота диверсий (друзьям владельца она безлимитна) и
     * консоль. Раньше на этот вопрос отвечал поиск учётки по почте из
     * настроек — то есть смена почты меняла владельца, а знать почту
     * приходилось всем спрашивающим.
     */
    Optional<String> ownerUid();

    /** Сколько всего учёток заведено: строка дашборда. */
    long countAccounts();

    /** Сколько человек зарегистрировалось за период: строка отчёта о расходе. */
    long countRegisteredBetween(Instant from, Instant to);

    /**
     * Учётка в объёме, который нужен соседям.
     *
     * <p>Ни хеша пароля, ни ника: первое не покидает область вовсе, второе
     * принадлежит карточке игрока и спрашивается у {@code profile}.
     */
    record Account(String uid, String email, boolean guest, int tokenVersion,
                   boolean passwordSet, Long registeredAtMs, boolean admin, boolean owner) {
    }
}
