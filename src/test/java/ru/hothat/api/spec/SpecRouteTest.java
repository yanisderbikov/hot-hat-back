package ru.hothat.api.spec;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import ru.hothat.support.ApiSpec;
import ru.hothat.support.Controllers;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Адрес объявлен один раз, и снимок знает ровно объявленные адреса.
 *
 * <p>Дубль пары «метод + путь» по спецификации не виден вовсе: {@code paths} —
 * это отображение, и второе объявление того же адреса просто вытесняет первое.
 * Поднимется при этом контейнер или нет, зависит от того, различаются ли
 * отображения ещё чем-нибудь, — то есть поломка может дожить до боевого
 * сервера. Поэтому дубли ищутся по объявлениям контроллеров, а не по снимку.
 *
 * <p>Вторая проверка — что снимок не отстал. Полный ответ приложения сверяет
 * медленный {@link OpenApiSnapshotFreshnessTest}, но набор адресов виден и без
 * подъёма контекста: он выводится из тех же аннотаций, по которым его строит
 * springdoc. Отставание снимка на удалённый или добавленный адрес ловится
 * здесь — за секунды и без базы.
 */
class SpecRouteTest {

    private final ApiSpec spec = ApiSpec.fromSnapshot();

    @Test
    @DisplayName("Ни одна пара «метод + путь» не объявлена дважды")
    void noRouteIsDeclaredTwice() {
        Map<String, List<String>> declaredAt = new LinkedHashMap<>();
        for (Controllers.Route route : Controllers.routes()) {
            declaredAt.computeIfAbsent(route.address(), key -> new ArrayList<>()).add(route.declaredAt());
        }

        List<String> duplicates = declaredAt.entrySet().stream()
                .filter(entry -> entry.getValue().size() > 1)
                .map(entry -> entry.getKey() + " ← " + entry.getValue())
                .sorted()
                .toList();

        assertThat(duplicates)
                .as("адреса, объявленные больше одного раза")
                .isEmpty();
    }

    @Test
    @DisplayName("Снимок спецификации знает ровно те адреса /api/v2, что объявлены контроллерами")
    void snapshotCoversExactlyTheDeclaredRoutes() {
        Set<String> declared = Controllers.v2Routes().stream()
                .map(Controllers.Route::address)
                .collect(Collectors.toCollection(TreeSet::new));
        Set<String> documented = spec.v2Operations().stream()
                .map(ApiSpec.Operation::address)
                .collect(Collectors.toCollection(TreeSet::new));

        Set<String> onlyInCode = new TreeSet<>(declared);
        onlyInCode.removeAll(documented);
        Set<String> onlyInSnapshot = new TreeSet<>(documented);
        onlyInSnapshot.removeAll(declared);

        assertThat(onlyInCode)
                .as("адреса есть в коде, но их нет в снимке — снимок отстал")
                .isEmpty();
        assertThat(onlyInSnapshot)
                .as("адреса есть в снимке, но их нет в коде — снимок обещает то, чего больше нет")
                .isEmpty();
    }
}
