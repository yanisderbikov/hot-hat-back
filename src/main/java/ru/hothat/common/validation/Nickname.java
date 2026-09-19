package ru.hothat.common.validation;

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
 * Ник игрока: латиница, первый знак — буква, 3–20 знаков.
 *
 * <p>Ровно то же, что проверяет {@code Ids.NICKNAME} внутри сервиса. Правило
 * повторено здесь, а не заимствовано оттуда, потому что форма запроса обязана
 * отвергаться до входа в сценарий; но записано оно один раз — до этого одно
 * и то же выражение стояло в четырёх DTO двух областей.
 */
@Documented
@Constraint(validatedBy = {})
@Target({FIELD, PARAMETER, RECORD_COMPONENT, ANNOTATION_TYPE})
@Retention(RetentionPolicy.RUNTIME)
@jakarta.validation.constraints.Pattern(regexp = "^[A-Za-z][A-Za-z0-9_]{2,19}$")
@jakarta.validation.ReportAsSingleViolation
public @interface Nickname {

    String message() default "Некорректный ник.";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
