package ru.hothat.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Собранная спецификация как данные, по которым можно спрашивать.
 *
 * <p>Сторожа контракта читают снимок из {@code docs/api}, а не поднимают
 * контекст: спецификация — это документ, и разбор документа не требует ни
 * базы, ни веб-сервера. Что снимок не разошёлся с живым приложением,
 * проверяет отдельный медленный тест.
 */
public final class ApiSpec {

    /** Снимок лежит в репозитории и обновляется вместе с кодом. */
    public static final Path SNAPSHOT_FILE = Path.of("docs/api/openapi-snapshot.json");

    private static final Set<String> HTTP_METHODS =
            Set.of("get", "put", "post", "delete", "options", "head", "patch", "trace");

    private static final String SCHEMA_REF_PREFIX = "#/components/schemas/";

    /** Одна операция спецификации вместе с адресом, по которому она найдена. */
    public record Operation(String httpMethod, String path, JsonNode node) {

        public String address() {
            return httpMethod + " " + path;
        }
    }

    private final JsonNode root;

    private ApiSpec(JsonNode root) {
        this.root = root;
    }

    public static ApiSpec fromSnapshot() {
        try {
            return new ApiSpec(new ObjectMapper().readTree(Files.readString(SNAPSHOT_FILE)));
        } catch (Exception failure) {
            throw new IllegalStateException("Не читается снимок спецификации " + SNAPSHOT_FILE.toAbsolutePath(),
                    failure);
        }
    }

    public static ApiSpec of(JsonNode root) {
        return new ApiSpec(root);
    }

    public JsonNode root() {
        return root;
    }

    public List<Operation> operations() {
        List<Operation> operations = new ArrayList<>();
        JsonNode paths = root.path("paths");
        paths.fieldNames().forEachRemaining(path -> {
            JsonNode byMethod = paths.get(path);
            byMethod.fieldNames().forEachRemaining(method -> {
                if (HTTP_METHODS.contains(method)) {
                    operations.add(new Operation(method.toUpperCase(), path, byMethod.get(method)));
                }
            });
        });
        operations.sort(Comparator.comparing(Operation::path).thenComparing(Operation::httpMethod));
        return operations;
    }

    /** Новая поверхность: у старых адресов свои правила, и сторожа их не трогают. */
    public List<Operation> v2Operations() {
        return operations().stream().filter(operation -> operation.path().startsWith("/api/v2")).toList();
    }

    /** Объявленные схемы в порядке спецификации. */
    public Map<String, JsonNode> schemas() {
        Map<String, JsonNode> schemas = new LinkedHashMap<>();
        JsonNode declared = root.path("components").path("schemas");
        declared.fieldNames().forEachRemaining(name -> schemas.put(name, declared.get(name)));
        return schemas;
    }

    /**
     * Схемы, до которых можно дойти от названных операций.
     *
     * <p>Считать надо именно так: в снимке остаются и схемы старых адресов, а
     * правила новой поверхности к ним не применяются.
     */
    public Set<String> schemasReachableFrom(List<Operation> operations) {
        Map<String, JsonNode> schemas = schemas();
        Set<String> reachable = new LinkedHashSet<>();
        Deque<String> queue = new ArrayDeque<>();
        operations.forEach(operation -> queue.addAll(schemaRefsIn(operation.node())));
        while (!queue.isEmpty()) {
            String name = queue.poll();
            if (!reachable.add(name)) {
                continue;
            }
            JsonNode schema = schemas.get(name);
            if (schema != null) {
                queue.addAll(schemaRefsIn(schema));
            }
        }
        return reachable;
    }

    /** Все имена схем, на которые ссылается поддерево, включая вложенные. */
    public static Set<String> schemaRefsIn(JsonNode node) {
        Set<String> refs = new LinkedHashSet<>();
        collectRefs(node, refs);
        return refs;
    }

    private static void collectRefs(JsonNode node, Set<String> refs) {
        if (node == null) {
            return;
        }
        if (node.isObject()) {
            JsonNode ref = node.get("$ref");
            if (ref != null && ref.isTextual() && ref.textValue().startsWith(SCHEMA_REF_PREFIX)) {
                refs.add(ref.textValue().substring(SCHEMA_REF_PREFIX.length()));
            }
            node.fields().forEachRemaining(field -> {
                if (!"$ref".equals(field.getKey())) {
                    collectRefs(field.getValue(), refs);
                }
            });
            return;
        }
        if (node.isArray()) {
            node.forEach(child -> collectRefs(child, refs));
        }
    }
}
