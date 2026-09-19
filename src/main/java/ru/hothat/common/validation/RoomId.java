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
 * Идентификатор комнаты: {@code hat-} и шестнадцать шестнадцатеричных цифр.
 *
 * <p>Заменяет три разных ручных проверки {@code room_id}, живущих сегодня в
 * {@code Ids.requireRoomId}, {@code CleanupController.cleanRoomId} и
 * {@code AdminController} (там идентификатор просто приводится к нижнему
 * регистру и не проверяется вовсе).
 */
@Documented
@Constraint(validatedBy = {})
@Target({FIELD, PARAMETER, RECORD_COMPONENT, ANNOTATION_TYPE})
@Retention(RetentionPolicy.RUNTIME)
@jakarta.validation.constraints.Pattern(regexp = "^hat-[a-f0-9]{16}$")
@jakarta.validation.ReportAsSingleViolation
public @interface RoomId {

    String message() default "Некорректная игровая комната.";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
