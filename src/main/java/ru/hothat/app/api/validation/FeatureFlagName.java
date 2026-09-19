package ru.hothat.app.api.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.ANNOTATION_TYPE;
import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.ElementType.RECORD_COMPONENT;

/**
 * Имя возможности: закрытый набор, а не любая строка.
 *
 * <p>Закрывает G3 аудита. {@code FeatureFlagServiceImpl} держит кеш в
 * {@code ConcurrentHashMap} и заводит запись на каждое спрошенное имя, а
 * спрашивать может кто угодно — маршрут открыт до входа. Тысяча запросов с
 * разными именами — тысяча вечных записей в памяти. Пока имя приходило
 * свободной строкой, ограничить кеш было нечем; с закрытым набором ключей у
 * него ровно столько, сколько здесь перечислено.
 *
 * <p>Список тот же, что в {@code public/features.js}: клиент и так знает его
 * наизусть и предупреждает в консоли о незнакомом имени. Здесь то же правило
 * повторено на сервере — потому что клиент можно обойти, а кеш растёт на
 * сервере.
 *
 * <p>Проверка отвечает 400, а не «выключено». Старое поведение —
 * «неизвестное имя даёт {@code enabled:false}» — защищало от опечатки, которая
 * случайно включит фичу, и это остаётся верным: 400 тем более ничего не
 * включает. Зато опечатка теперь видна сразу, а не притворяется выключенной
 * возможностью.
 */
@Documented
@Constraint(validatedBy = {})
@Target({FIELD, PARAMETER, RECORD_COMPONENT, ANNOTATION_TYPE})
@Retention(RetentionPolicy.RUNTIME)
@jakarta.validation.constraints.Pattern(regexp = "^(?:bot_enabled|admin_feature)$")
@jakarta.validation.ReportAsSingleViolation
public @interface FeatureFlagName {

    String message() default "Неизвестная возможность: допустимы bot_enabled и admin_feature.";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
