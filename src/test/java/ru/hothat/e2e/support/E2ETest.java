package ru.hothat.e2e.support;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import javax.sql.DataSource;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Общая часть сквозных тестов: поднятое приложение, чистая база и вход.
 *
 * <p>Это медленные тесты: каждый поднимает приложение и ходит в базу. Они
 * отмечены тегом {@code e2e} и лежат отдельным пакетом, поэтому их можно
 * прогнать одни ({@code mvn -o test -Dtest='ru.hothat.e2e.*Test'}) и можно
 * пропустить целиком ({@code mvn -o test -DexcludedGroups=e2e}) — например,
 * когда docker-Postgres не поднят.
 *
 * <p>Базу тесты заводят себе сами: {@code hot_hat_e2e} на той же машине, что
 * и база приложения. Нужен только поднятый контейнер:
 * {@code docker compose up -d postgres}.
 *
 * <p>Порт случайный: чужие 8092 и 8093 тесту не нужны, а занятый порт не
 * должен ронять прогон.
 */
@Tag("e2e")
@ActiveProfiles("e2e")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public abstract class E2ETest {

    /**
     * Обойма из пяти мемов: без неё комната отвечает 409 DEFAULT_LOADOUT_REQUIRED.
     *
     * <p>Эти пять роликов заводятся в каталоге перед каждым тестом
     * ({@link E2EDatabase#seedMemeCatalog}). Выдуманных идентификаторов здесь
     * больше нет: адрес обоймы проверяет, что мем существует, и обойма из
     * ничего теперь отвергается — как и должна была всегда.
     */
    protected static final List<String> LOADOUT = List.of(
            "builtin-e2e-loadout-1", "builtin-e2e-loadout-2", "builtin-e2e-loadout-3",
            "builtin-e2e-loadout-4", "builtin-e2e-loadout-5");

    private static final String PASSWORD = "e2e-password";

    @LocalServerPort
    private int port;

    @Autowired
    private DataSource dataSource;

    protected ApiClient api;

    /** Порт поднятого приложения — сокетам нужен адрес, а не клиент. */
    protected int port() {
        return port;
    }

    /**
     * Адрес канала под личностью игрока.
     *
     * <p>Токен идёт строкой запроса, а не заголовком: браузерный WebSocket
     * заголовков не ставит, и сервер читает его именно отсюда.
     */
    protected URI ws(String path, Player player) {
        return URI.create("ws://localhost:" + port + path
                + "?token=" + URLEncoder.encode(player.token(), StandardCharsets.UTF_8));
    }

    /** Прямой доступ к базе нужен ровно там, где проверяется, что наружу не уехало. */
    protected DataSource database() {
        return dataSource;
    }

    /**
     * Базу заводим здесь, а не в {@code @BeforeAll}: пул соединений создаётся
     * вместе с контекстом, то есть раньше любого метода теста.
     */
    @DynamicPropertySource
    static void datasourceUrl(DynamicPropertyRegistry registry) {
        E2EDatabase.ensureExists();
        registry.add("spring.datasource.url", E2EDatabase::url);
    }

    @BeforeEach
    void resetWorld() {
        E2EDatabase.wipe(dataSource);
        // Посев идёт после очистки: TRUNCATE уносит и каталог мемов, посеянный
        // миграцией, а заряжать обойму нечем без него.
        E2EDatabase.seedMemeCatalog(dataSource, LOADOUT);
        api = new ApiClient(port);
    }

    /**
     * Чистим и после себя. Перед тестом — обязательно (упавший прогон иначе
     * сломал бы следующий), после — из вежливости: база остаётся пустой,
     * и заглянувший в неё видит то, что оставил тест, а не прошлый мусор.
     */
    @AfterEach
    void leaveNothingBehind() {
        E2EDatabase.wipe(dataSource);
    }

    /** Регистрация: учётка заводится и сразу отдаёт пару токенов. */
    protected Player register(String nickname) {
        String email = nickname.toLowerCase() + "@e2e.test";
        ApiClient.Response response = api.anonymousPost("/api/v2/auth/accounts",
                Map.of("email", email, "password", PASSWORD, "nickname", nickname));
        assertThat(response.status()).as("регистрация %s: %s", nickname, response.raw()).isEqualTo(201);
        return new Player(response.text("/account/uid"), nickname, email, PASSWORD,
                response.text("/tokens/accessToken"));
    }

    /** Регистрация плюс заряженная обойма — тот, кого пустят в комнату. */
    protected Player registerArmed(String nickname) {
        Player player = register(nickname);
        ApiClient.Response response = api.put("/api/v2/profile/me/meme-loadout", player.token(),
                Map.of("memeIds", LOADOUT));
        assertThat(response.status()).as("обойма %s: %s", nickname, response.raw()).isEqualTo(200);
        return player;
    }
}
