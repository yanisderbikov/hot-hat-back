package ru.hothat.e2e.support;

/**
 * Зарегистрированный игрок с открытой сессией.
 *
 * @param token access-токен: его тест кладёт в заголовок так же, как браузер
 */
public record Player(String uid, String nickname, String email, String password, String token) {

    public Player withToken(String newToken) {
        return new Player(uid, nickname, email, password, newToken);
    }

    public Player withNickname(String newNickname) {
        return new Player(uid, newNickname, email, password, token);
    }
}
