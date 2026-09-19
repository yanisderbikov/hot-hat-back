package ru.hothat.e2e;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import ru.hothat.e2e.support.ApiClient;
import ru.hothat.e2e.support.E2EDatabase;
import ru.hothat.e2e.support.E2ETest;
import ru.hothat.e2e.support.Player;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Хеш пароля и версия токена не уезжают в браузер.
 *
 * <p>Оба поля лежат в той же строке таблицы, что почта и ник, и отдать их
 * наружу проще всего случайно — вернув строку целиком. Поэтому проверяются
 * не только имена полей, но и само хранимое значение хеша: переименование
 * поля такую проверку не обмануло бы, а вот поиск по имени — обманул бы.
 */
class AccountSecretsE2ETest extends E2ETest {

    private static final String[] FORBIDDEN_NAMES = {
            "passwordHash", "password_hash", "tokenVersion", "token_version"};

    @Test
    @DisplayName("Ни один ответ поверхности не содержит хеша пароля и версии токена")
    void secretsNeverLeave() {
        Player anya = registerArmed("Anya");
        Player boris = registerArmed("Boris");
        // Ищем по почте, а не по uid: в таблице лежит внутренний ключ строки,
        // а наружу едет публичный идентификатор игрока — это разные вещи.
        String hash = E2EDatabase.single(database(),
                "SELECT password_hash FROM v2.user_account WHERE email = '" + anya.email() + "'");
        assertThat(hash).as("пароль вообще хранится хешем").isNotBlank().startsWith("$2");

        ApiClient.Response created = api.post("/api/v2/room", anya.token(), Map.of("name", "Тихая"));
        String roomId = created.text("/room/roomId");
        ApiClient.Response session = api.anonymousPost("/api/v2/auth/sessions",
                Map.of("email", anya.email(), "password", anya.password()));

        Map<String, ApiClient.Response> answers = new LinkedHashMap<>();
        answers.put("создание комнаты", created);
        answers.put("вход", session);
        answers.put("обновление пары токенов", api.anonymousPost("/api/v2/auth/sessions/renewal",
                Map.of("refreshToken", session.text("/tokens/refreshToken"))));
        answers.put("своя учётка", api.get("/api/v2/auth/me", anya.token()));
        answers.put("блокировка", api.get("/api/v2/auth/me/ban-state", anya.token()));
        answers.put("свой профиль", api.get("/api/v2/profile/me", anya.token()));
        answers.put("чужая карточка", api.get("/api/v2/profile/players/" + anya.uid(), boris.token()));
        answers.put("друзья", api.get("/api/v2/friends", anya.token()));
        answers.put("входящие заявки", api.get("/api/v2/friends/requests/incoming", anya.token()));
        answers.put("переписки", api.get("/api/v2/chat/threads", anya.token()));
        answers.put("входящие", api.get("/api/v2/chat/inbox", anya.token()));
        answers.put("снимок комнаты", api.get("/api/v2/room/" + roomId, anya.token()));

        answers.forEach((what, response) -> {
            assertThat(response.status()).as("%s ответил %s", what, response.status()).isBetween(200, 299);
            assertThat(response.raw()).as("%s: в ответе хранимый хеш пароля", what).doesNotContain(hash);
            assertThat(response.raw()).as("%s: в ответе служебное поле учётки", what)
                    .doesNotContain(FORBIDDEN_NAMES);
        });
    }

    @Test
    @DisplayName("Регистрация отдаёт токены и учётку, но не то, чем проверяют пароль")
    void registrationAnswerIsClean() {
        ApiClient.Response registered = api.anonymousPost("/api/v2/auth/accounts",
                Map.of("email", "clean@e2e.test", "password", "e2e-password", "nickname", "Clean"));

        assertThat(registered.status()).isEqualTo(201);
        // Положительная половина: ответ и правда несёт учётку — иначе
        // «секретов нет» было бы правдой и про пустое тело.
        assertThat(registered.text("/account/email")).isEqualTo("clean@e2e.test");
        assertThat(registered.text("/tokens/accessToken")).isNotBlank();
        assertThat(registered.raw()).doesNotContain(FORBIDDEN_NAMES);

        String hash = E2EDatabase.single(database(),
                "SELECT password_hash FROM v2.user_account WHERE email = 'clean@e2e.test'");
        assertThat(registered.raw()).doesNotContain(hash);
    }
}
