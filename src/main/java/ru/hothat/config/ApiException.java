package ru.hothat.config;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * Точный аналог `Object.assign(new Error(code), {status})` из Vercel-функций:
 * машинный код уходит клиенту в поле `code`, человеческий текст подставляет
 * {@link GlobalExceptionHandler} по тому же словарю, что был в JS.
 */
@Getter
public class ApiException extends RuntimeException {

    private final int status;
    private final String code;

    public ApiException(String code, int status) {
        super(code);
        this.code = code;
        this.status = status;
    }

    public static ApiException of(String code) {
        return new ApiException(code, 400);
    }

    public static ApiException of(String code, int status) {
        return new ApiException(code, status);
    }

    public static ApiException of(String code, HttpStatus status) {
        return new ApiException(code, status.value());
    }
}
