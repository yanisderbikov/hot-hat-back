package ru.hothat.api.spec;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import ru.hothat.support.ApiSpec;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * У каждого параметра назван тип.
 *
 * <p>Ловушка тут неочевидная и уже срабатывала. Стоит написать на параметре
 * вложенную {@code @Schema} — например, чтобы добавить пример, — как springdoc
 * перестаёт выводить тип из подписи метода и берёт только то, что названо в
 * аннотации. Тип при этом не назван нигде, и в спецификации остаётся пустая
 * схема. Генератор клиента выпустит по такому параметру нетипизированное
 * значение, а человек прочтёт «строка» и ошибётся.
 *
 * <p>Проверяется поэтому не наличие аннотации, а результат: тип обязан
 * оказаться в собранной спецификации, каким бы путём он туда ни попал.
 */
class SpecParameterTypeTest {

    private final ApiSpec spec = ApiSpec.fromSnapshot();

    @Test
    @DisplayName("У каждого параметра каждой операции /api/v2 назван тип")
    void everyParameterHasAType() {
        List<String> typeless = new ArrayList<>();

        for (ApiSpec.Operation operation : spec.v2Operations()) {
            for (JsonNode parameter : operation.node().path("parameters")) {
                JsonNode schema = parameter.path("schema");
                if (!namesAType(schema)) {
                    typeless.add(operation.address() + ", параметр " + parameter.path("name").asText("<без имени>")
                            + ": " + schema);
                }
            }
        }

        assertThat(typeless)
                .as("параметры /api/v2, у которых в спецификации нет типа")
                .isEmpty();
    }

    @Test
    @DisplayName("У каждого параметра каждой операции /api/v2 есть описание")
    void everyParameterIsDescribed() {
        List<String> undescribed = new ArrayList<>();

        for (ApiSpec.Operation operation : spec.v2Operations()) {
            for (JsonNode parameter : operation.node().path("parameters")) {
                if (parameter.path("description").asText("").isBlank()) {
                    undescribed.add(operation.address() + ", параметр " + parameter.path("name").asText("<без имени>"));
                }
            }
        }

        assertThat(undescribed)
                .as("параметры /api/v2 без description")
                .isEmpty();
    }

    /**
     * Тип назван, если он либо написан прямо, либо это ссылка на схему, либо
     * составная форма — во всех трёх случаях у клиента есть чем описать
     * значение.
     */
    private static boolean namesAType(JsonNode schema) {
        return schema.hasNonNull("type")
                || schema.hasNonNull("$ref")
                || schema.hasNonNull("allOf")
                || schema.hasNonNull("oneOf")
                || schema.hasNonNull("anyOf");
    }
}
