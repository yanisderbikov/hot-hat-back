package ru.hothat.auth.spi;

/**
 * Подпись и разбор токенов доступа — дверь области личности наружу.
 *
 * <p>Раньше это был отдельный переходный сервис, лежавший рядом с движками
 * портала под именем {@code JwtTokenService}. Ключ подписи, срок жизни и
 * поколение токенов принадлежат области личности — она их выдаёт и она же их
 * гасит при бане и смене пароля, — поэтому интерфейс переехал к владельцу.
 * Спрашивают его четверо: фильтр запроса, сборщик личности, машинные сессии
 * записи и сам выпуск пары токенов.
 *
 * <p>Порт намеренно ничего не знает про учётку: он подписывает то, что ему
 * назвали, и разбирает то, что ему принесли. Решение «жив ли этот человек и
 * то ли у него поколение» принимает {@code PrincipalResolver}, сверяя разбор
 * с таблицей — иначе формула «кто вошёл» оказалась бы в двух местах.
 */
public interface AccessTokenPort {

    /**
     * @param tokenVersion номер поколения токенов пользователя; фильтр сверяет
     *                     его с базой, поэтому бан и смена пароля гасят
     *                     выданные ранее токены немедленно.
     */
    String createAccessToken(String uid, String email, int tokenVersion, boolean guest);

    /** Непрозрачная случайная строка: refresh не подписывается, а хранится хешем. */
    String createRefreshToken();

    String sha256(String value);

    boolean isValid(String token);

    String getUid(String token);

    String getEmail(String token);

    int getTokenVersion(String token);

    long accessTtlSeconds();

    long refreshTtlSeconds();
}
