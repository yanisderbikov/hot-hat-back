package ru.hothat.e2e;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import ru.hothat.e2e.support.ApiClient;
import ru.hothat.e2e.support.E2ETest;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Машинная поверхность закрыта заголовком с секретом.
 *
 * <p>Чинилось: раньше машинные адреса стояли в {@code permitAll}, а настоящая
 * проверка пряталась в теле метода — то есть по декларации были открыты всем.
 * Здесь проверяется обратное с двух сторон: без заголовка и с чужим секретом
 * приходит 401, с настоящим — работа делается. Односторонняя проверка была бы
 * зелёной и на наглухо сломанном адресе.
 */
class MachineSecretE2ETest extends E2ETest {

    private static final String SWEEP = "/api/v2/machine/maintenance/room-sweeps";
    private static final String HEADER = "X-Hot-Hat-Machine-Secret";
    /** Тот же, что в application-e2e.properties: hot-hat.cron-secret. */
    private static final String SECRET = "e2e-cron-secret";

    @Test
    @DisplayName("Машинный адрес без заголовка с секретом отвечает 401")
    void withoutHeader() {
        ApiClient.Response refused = api.anonymousPost(SWEEP, null);

        assertThat(refused.status()).isEqualTo(401);
    }

    @Test
    @DisplayName("Машинный адрес с чужим секретом отвечает 401")
    void withWrongSecret() {
        ApiClient.Response refused = sweep("wrong-machine-secret");

        assertThat(refused.status()).isEqualTo(401);
    }

    @Test
    @DisplayName("Машинный адрес с настоящим секретом пускает")
    void withRealSecret() {
        ApiClient.Response accepted = sweep(SECRET);

        assertThat(accepted.status()).as(accepted.raw()).isBetween(200, 299);
    }

    @Test
    @DisplayName("Пользовательский токен машинный адрес не открывает")
    void userTokenIsNotAMachine() {
        // Секрет машины и токен игрока — разные удостоверения; игрок не должен
        // запускать уборку только потому, что он вошёл.
        String playerToken = register("Anya").token();

        ApiClient.Response refused = api.post(SWEEP, playerToken, null);

        assertThat(refused.status()).isEqualTo(401);
    }

    private ApiClient.Response sweep(String secret) {
        return api.send("POST", SWEEP, null, null, Map.of(HEADER, secret));
    }
}
