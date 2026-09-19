package ru.hothat.dto.common;

/**
 * Единый формат ошибки. Форма сохранена с прежнего API: клиент разбирает
 * машинный code, а error показывает пользователю.
 *
 * @param error понятный человеку текст
 * @param code  машинный код: AUTH_REQUIRED, NICKNAME_TAKEN, VALIDATION_FAILED…
 */
public record ErrorDTO(String error, String code) {
}
