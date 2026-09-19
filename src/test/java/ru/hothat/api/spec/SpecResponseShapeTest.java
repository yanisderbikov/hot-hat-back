package ru.hothat.api.spec;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import ru.hothat.support.ApiSpec;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Форма ответа: именованная схема либо пустое тело — и ничего третьего.
 *
 * <p>Словарь в ответе — это отсутствие договора: сервер волен положить туда
 * что угодно, а клиент вынужден угадывать. Именно так и жил прежний API, и
 * ровно поэтому фронтенд ломался молча — поле переставало приходить, а
 * спецификация об этом ничего не знала.
 *
 * <p>Ссылка в никуда ничем не лучше словаря: имя схемы есть, а формы за ним
 * нет, и генератор клиента выпустит по такому полю нетипизированный объект.
 */
class SpecResponseShapeTest {

    private final ApiSpec spec = ApiSpec.fromSnapshot();

    @Test
    @DisplayName("Успешный ответ /api/v2 — это ссылка на именованную схему или пустое тело у 204")
    void successfulResponsesAreNamedSchemasOrEmpty() {
        List<String> shapeless = new ArrayList<>();
        for (ApiSpec.Operation operation : spec.v2Operations()) {
            JsonNode responses = operation.node().path("responses");
            responses.fieldNames().forEachRemaining(status -> {
                if (!status.startsWith("2")) {
                    return;
                }
                JsonNode content = responses.path(status).path("content");
                if (content.isMissingNode() || content.isEmpty()) {
                    // Пустое тело допустимо только там, где так и объявлено.
                    if (!"204".equals(status)) {
                        shapeless.add(operation.address() + " → " + status + ": тело не описано, а статус не 204");
                    }
                    return;
                }
                content.fieldNames().forEachRemaining(mediaType -> {
                    JsonNode schema = content.path(mediaType).path("schema");
                    if (!schema.hasNonNull("$ref")) {
                        shapeless.add(operation.address() + " → " + status + " (" + mediaType + "): " + schema);
                    }
                });
            });
        }

        assertThat(shapeless)
                .as("операции /api/v2, отвечающие безымянной формой")
                .isEmpty();
    }

    @Test
    @DisplayName("Каждая ссылка на схему ведёт в объявленную схему, а не в пустоту")
    void everySchemaReferenceResolves() {
        Set<String> declared = spec.schemas().keySet();

        List<String> dangling = spec.schemasReachableFrom(spec.v2Operations()).stream()
                .filter(name -> !declared.contains(name))
                .sorted()
                .toList();

        assertThat(dangling)
                .as("имена схем, на которые ссылается /api/v2, но которых нет в components.schemas")
                .isEmpty();
    }

    @Test
    @DisplayName("Ни одна схема ответа /api/v2 не подменена словарём с произвольными ключами")
    void namedSchemasAreNotDisguisedMaps() {
        var schemas = spec.schemas();

        List<String> maps = spec.schemasReachableFrom(spec.v2Operations()).stream()
                .filter(schemas::containsKey)
                // additionalProperties у самой схемы означает «ключи заранее
                // неизвестны»: имя есть, а перечня полей нет.
                .filter(name -> schemas.get(name).hasNonNull("additionalProperties"))
                .sorted()
                .toList();

        assertThat(maps)
                .as("именованные схемы /api/v2, которые на деле являются словарём")
                .isEmpty();
    }
}
