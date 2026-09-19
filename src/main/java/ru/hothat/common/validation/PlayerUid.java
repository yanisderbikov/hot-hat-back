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
 * Идентификатор игрока: то, что лежит в колонке {@code app_user.uid}.
 *
 * <p>Форма взята по колонке (160 знаков), а не по генератору
 * ({@code AuthServiceImpl.newUid} — ровно 28 знаков из букв и цифр).
 * Так сделано намеренно: правило на входе не должно отвергать личность,
 * которую база хранит и признаёт. Незнакомый идентификатор всё равно
 * упрётся в сервис и получит 403 или 404 — это честнее, чем 400 «некорректный
 * игрок» человеку, который существует.
 *
 * <p>Заменяет два разошедшихся определения одного и того же: область друзей
 * требовала {@code ^[A-Za-z0-9]{28}$}, область переписки —
 * {@code ^[A-Za-z0-9_-]{1,160}$}, а публичная карточка не проверяла ничего.
 * Один и тот же игрок мог оказаться приемлемым собеседником и неприемлемым
 * другом.
 */
@Documented
@Constraint(validatedBy = {})
@Target({FIELD, PARAMETER, RECORD_COMPONENT, ANNOTATION_TYPE})
@Retention(RetentionPolicy.RUNTIME)
@jakarta.validation.constraints.Pattern(regexp = "^[A-Za-z0-9_-]{1,160}$")
@jakarta.validation.ReportAsSingleViolation
public @interface PlayerUid {

    String message() default "Некорректный игрок.";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
