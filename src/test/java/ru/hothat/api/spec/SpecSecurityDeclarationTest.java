package ru.hothat.api.spec;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import ru.hothat.support.ApiSpec;
import ru.hothat.support.Controllers;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Про каждый адрес сказано, нужен ли ему пропуск.
 *
 * <p>Умолчание здесь опаснее ошибки. Контроллер без единой аннотации охраны
 * попадает в спецификацию точно так же, как публичный: у операции просто нет
 * поля {@code security}, и читающий делает единственный возможный вывод —
 * «вход свободный». Охрана при этом на месте, и вызов вернёт 401; расходится
 * не поведение, а обещание, и расходится молча.
 *
 * <p>Поэтому «публично» полагается объявлять словами — пустым
 * {@link SecurityRequirements}. Тогда у забытой аннотации нет вида
 * правильного ответа.
 */
class SpecSecurityDeclarationTest {

    private final ApiSpec spec = ApiSpec.fromSnapshot();

    @Test
    @DisplayName("Каждый контроллер /api/v2 объявляет охрану: либо схему пропуска, либо явную пустоту")
    void everyV2ControllerDeclaresItsSecurity() {
        List<String> silent = Controllers.v2Controllers().stream()
                .filter(controller -> !declaresSecurity(controller))
                .map(Class::getName)
                .toList();

        assertThat(silent)
                .as("контроллеры /api/v2 без @SecurityRequirement и без пустого @SecurityRequirements")
                .isEmpty();
    }

    @Test
    @DisplayName("Ни один адрес /api/v2 не унаследовал охрану от умолчания: она названа у каждого")
    void everyV2OperationCarriesSecurityInTheSpecification() {
        List<String> silent = spec.v2Operations().stream()
                .filter(operation -> !operation.node().has("security"))
                .map(ApiSpec.Operation::address)
                .toList();

        assertThat(silent)
                .as("операции /api/v2, у которых в снимке нет поля security")
                .isEmpty();
    }

    @Test
    @DisplayName("Защищённый адрес /api/v2 называет схему пропуска, а не пустой список")
    void protectedOperationsNameTheirScheme() {
        List<String> mismatched = new ArrayList<>();

        for (ApiSpec.Operation operation : spec.v2Operations()) {
            boolean openInSpecification = operation.node().path("security").isEmpty();
            boolean openInCode = isDeclaredPublic(controllerOf(operation));
            if (openInSpecification != openInCode) {
                mismatched.add(operation.address() + ": в снимке "
                        + (openInSpecification ? "публичный" : "защищённый") + ", а в коде "
                        + (openInCode ? "публичный" : "защищённый"));
            }
        }

        assertThat(mismatched)
                .as("адреса, у которых снимок и код по-разному отвечают на вопрос «нужен ли пропуск»")
                .isEmpty();
    }

    /** Контроллер, объявивший этот адрес: сверять снимок надо с тем, кто его порождает. */
    private static Class<?> controllerOf(ApiSpec.Operation operation) {
        return Controllers.v2Routes().stream()
                .filter(route -> route.address().equals(operation.address()))
                .map(Controllers.Route::controller)
                .findFirst()
                .orElseThrow(() -> new AssertionError("В снимке есть адрес " + operation.address()
                        + ", которого нет ни в одном контроллере"));
    }

    private static boolean declaresSecurity(Class<?> controller) {
        return isDeclaredPublic(controller)
                || controller.getAnnotationsByType(SecurityRequirement.class).length > 0
                || anyMethodDeclaresSecurity(controller);
    }

    /**
     * Пустой {@code @SecurityRequirements} — это и есть слово «публично»:
     * контейнер без единого требования springdoc переводит в {@code security: []}.
     */
    private static boolean isDeclaredPublic(Class<?> controller) {
        SecurityRequirements container = controller.getAnnotation(SecurityRequirements.class);
        return container != null && container.value().length == 0;
    }

    private static boolean anyMethodDeclaresSecurity(Class<?> controller) {
        for (Method method : controller.getDeclaredMethods()) {
            if (method.getAnnotationsByType(SecurityRequirement.class).length > 0
                    || method.getAnnotation(SecurityRequirements.class) != null) {
                return true;
            }
        }
        return false;
    }
}
