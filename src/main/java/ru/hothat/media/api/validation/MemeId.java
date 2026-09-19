package ru.hothat.media.api.validation;

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
import static java.lang.annotation.ElementType.TYPE_USE;

/**
 * Идентификатор мема в том виде, в каком его признаёт хранилище:
 * {@code meme-…} у игроцкого ролика, {@code builtin-…} у посеянного.
 *
 * <p>Форма повторяет {@code Ids.MEME_ID} — единственную проверку, через
 * которую сегодня проходит идентификатор перед тем, как стать частью пути
 * объекта. Аннотация живёт в области media, а не в {@code common}, потому что
 * за её пределами это понятие не встречается; понадобится соседям — переедет.
 *
 * <p>Публикация и билеты пользуются более узким правилом (только
 * {@code meme-…}): встроенные мемы заводит посев, и загружать файлы в их папку
 * игроку незачем. Это правило объявлено на месте, в своих DTO.
 */
@Documented
@Constraint(validatedBy = {})
// TYPE_USE — чтобы правило вешалось и на элемент списка: обойма приезжает
// списком идентификаторов, и проверять его поэлементно можно только так.
@Target({FIELD, PARAMETER, RECORD_COMPONENT, ANNOTATION_TYPE, TYPE_USE})
@Retention(RetentionPolicy.RUNTIME)
@jakarta.validation.constraints.Pattern(regexp = "^(?:meme|builtin)-[A-Za-z0-9_-]{6,100}$")
@jakarta.validation.ReportAsSingleViolation
public @interface MemeId {

    String message() default "Некорректный мем.";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
