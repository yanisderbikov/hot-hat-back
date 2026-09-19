package ru.hothat.api.spec;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import ru.hothat.e2e.support.E2EDatabase;
import ru.hothat.support.ApiSpec;

import java.util.Set;
import java.util.TreeSet;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Снимок спецификации — то же, что отдаёт приложение.
 *
 * <p>Остальные сторожа контракта читают снимок и потому быстры. Ценность этой
 * быстроты держится ровно на одном: снимок не должен расходиться с живой
 * сборкой. Разойдясь, он превращает всю проверку в обряд — зелёные тесты
 * стерегут документ, которого больше нет.
 *
 * <p>Тест медленный: он поднимает приложение целиком и потому помечен тегом
 * {@code e2e} — тем же, что и сквозные тесты, и по той же причине (нужен
 * docker-Postgres на 5462). Прогнать одни: {@code mvn test -Dgroups=e2e};
 * пропустить: {@code mvn test -DexcludedGroups=e2e}.
 *
 * <p>Снимок обновляется тем же ответом, который тест и сверяет:
 * <pre>
 * curl -s http://localhost:8092/v3/api-docs &gt; docs/api/openapi-snapshot.json
 * </pre>
 */
@Tag("e2e")
@ActiveProfiles("e2e")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class OpenApiSnapshotFreshnessTest {

    /**
     * База нужна не сама по себе, а чтобы приложение вообще поднялось:
     * спецификация собирается из контроллеров, а те не создаются без своих
     * зависимостей. Своя база — чтобы не трогать данные разработки.
     */
    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        E2EDatabase.ensureExists();
        registry.add("spring.datasource.url", E2EDatabase::url);
    }

    @Autowired
    private TestRestTemplate http;

    @Test
    @DisplayName("Снимок в docs/api совпадает с тем, что приложение отдаёт по /v3/api-docs")
    void snapshotMatchesTheAssembledSpecification() throws Exception {
        JsonNode assembled = new ObjectMapper().readTree(
                http.getForObject("/v3/api-docs", String.class));
        ApiSpec live = ApiSpec.of(assembled);
        ApiSpec snapshot = ApiSpec.fromSnapshot();

        assertThat(addresses(live))
                .as("набор адресов в снимке %s отстал от приложения; обновить: "
                        + "curl -s http://localhost:8092/v3/api-docs > %s",
                        ApiSpec.SNAPSHOT_FILE, ApiSpec.SNAPSHOT_FILE)
                .isEqualTo(addresses(snapshot));
        assertThat(new TreeSet<>(live.schemas().keySet()))
                .as("набор схем в снимке отстал от приложения")
                .isEqualTo(new TreeSet<>(snapshot.schemas().keySet()));
        assertThat(assembled)
                .as("снимок совпадает с приложением по адресам и схемам, но расходится "
                        + "в подробностях — описаниях, примерах или ответах")
                .isEqualTo(snapshot.root());
    }

    private static Set<String> addresses(ApiSpec spec) {
        Set<String> addresses = new TreeSet<>();
        spec.operations().forEach(operation -> addresses.add(operation.address()));
        return addresses;
    }
}
