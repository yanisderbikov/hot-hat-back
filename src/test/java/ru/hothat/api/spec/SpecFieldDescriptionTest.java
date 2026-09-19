package ru.hothat.api.spec;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import ru.hothat.support.ApiSpec;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * У каждого поля есть подпись.
 *
 * <p>Подпись — единственное место, где сказано, что поле значит: {@code null}
 * это «нет аватара» или «не загрузили», а {@code 0} — «ничья» или «счёт ещё не
 * считали». Без неё клиент угадывает, и угадывает по-своему на каждом экране.
 *
 * <p>Теряется подпись молча. Достаточно, чтобы поле-ссылку собрал не тот
 * преобразователь схем: в OpenAPI 3.0 сосед у {@code $ref} запрещён, и
 * {@code @Schema(description = …)} рядом со ссылкой исчезает без единого
 * предупреждения. Ровно из-за этого проект и переведён на 3.1.
 */
class SpecFieldDescriptionTest {

    private final ApiSpec spec = ApiSpec.fromSnapshot();

    @Test
    @DisplayName("У каждого поля каждой схемы /api/v2 есть непустое описание")
    void everyFieldOfEveryReachableSchemaIsDescribed() {
        Map<String, JsonNode> schemas = spec.schemas();
        List<String> undescribed = new ArrayList<>();

        for (String name : spec.schemasReachableFrom(spec.v2Operations())) {
            JsonNode schema = schemas.get(name);
            if (schema == null) {
                // Про несуществующую схему говорит SpecResponseShapeTest;
                // здесь она не должна превращаться в мнимый успех.
                continue;
            }
            JsonNode properties = schema.path("properties");
            properties.fieldNames().forEachRemaining(field -> {
                String description = properties.path(field).path("description").asText("");
                if (description.isBlank()) {
                    undescribed.add(name + "." + field);
                }
            });
        }

        assertThat(undescribed)
                .as("поля схем /api/v2 без description")
                .isEmpty();
    }

    @Test
    @DisplayName("У каждой схемы /api/v2 есть собственное описание")
    void everyReachableSchemaIsDescribed() {
        Map<String, JsonNode> schemas = spec.schemas();

        List<String> undescribed = spec.schemasReachableFrom(spec.v2Operations()).stream()
                .filter(schemas::containsKey)
                .filter(name -> !isDescribed(schemas.get(name)))
                .sorted()
                .toList();

        assertThat(undescribed)
                .as("схемы /api/v2 без description")
                .isEmpty();
    }

    /** У перечисления описание живёт на самом типе, а не на его значениях. */
    private static boolean isDescribed(JsonNode schema) {
        return !schema.path("description").asText("").isBlank();
    }
}
