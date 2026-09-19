package ru.hothat.config.swagger;

import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;

import java.util.List;

/**
 * Дописывает в спецификацию отказы, которые выдаёт не сценарий, а охрана.
 *
 * <p>Зачем: отказ по удостоверению не проходит через контроллер вовсе — его
 * пишет цепочка Spring Security, и в методе для него нет ни строки, к которой
 * можно было бы приписать {@code @ApiResponse}. В итоге двенадцать машинных
 * операций были описаны единственным ответом 200, хотя без заголовка с
 * секретом отвечают 401, а двадцать три админских — единственным 200, хотя
 * обычному игроку отвечают 403. Спецификация обещала то, чего не бывает.
 *
 * <p>Правило записано здесь один раз и выводится из самой охраны, а не из
 * списка адресов: новый контроллер той же области получает те же отказы сам.
 * Признак берётся тот же, по которому охрана и работает, — пакет машинной
 * поверхности и выражение {@code @PreAuthorize} с привилегированной ролью.
 *
 * <p>Уже описанный в коде ответ не трогается: если у операции есть свой 403 с
 * подробностями, он и остаётся.
 */
@Component
public class SecurityRefusalsCustomizer implements OperationCustomizer {

    /** Машинная поверхность: удостоверение — заголовок с секретом, отказ — 401. */
    private static final String MACHINE_API_PACKAGE = "ru.hothat.machine.api";
    /**
     * Две роли, а не одна: консоль открыта администратору, реестр учёток —
     * только владельцу. Отказ у обеих один и тот же, {@code ADMIN_REQUIRED},
     * поэтому и ответ в спецификации общий.
     */
    private static final List<String> PRIVILEGED_ROLES = List.of("hasRole('ADMIN')", "hasRole('OWNER')");

    @Override
    public Operation customize(Operation operation, HandlerMethod handlerMethod) {
        Class<?> controller = handlerMethod.getBeanType();
        if (controller.getPackageName().startsWith(MACHINE_API_PACKAGE)) {
            addRefusal(operation, "401",
                    "Удостоверение машинного актора не подошло или его не прислали");
        }
        if (requiresPrivilegedRole(handlerMethod)) {
            addRefusal(operation, "403", "ADMIN_REQUIRED: недостаточно прав");
        }
        return operation;
    }

    /**
     * Роль проверяется и на методе, и на классе: у консоли администратора
     * предикат стоит на классе целиком, а у отдельных владельческих операций —
     * на методе.
     */
    private static boolean requiresPrivilegedRole(HandlerMethod handlerMethod) {
        return declares(handlerMethod.getMethodAnnotation(PreAuthorize.class))
                || declares(handlerMethod.getBeanType().getAnnotation(PreAuthorize.class));
    }

    private static boolean declares(PreAuthorize guard) {
        return guard != null && PRIVILEGED_ROLES.stream().anyMatch(role -> guard.value().contains(role));
    }

    private static void addRefusal(Operation operation, String code, String description) {
        ApiResponses responses = operation.getResponses();
        if (responses == null) {
            responses = new ApiResponses();
            operation.setResponses(responses);
        }
        // Тело у отказа общее для всего API, и описывать его здесь заново
        // значило бы разойтись с тем, что пишет обработчик ошибок.
        responses.computeIfAbsent(code, key -> new ApiResponse().description(description));
    }
}
