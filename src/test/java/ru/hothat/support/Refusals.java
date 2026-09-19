package ru.hothat.support;

import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import ru.hothat.config.ApiException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Отказ проверяется вместе с кодом и статусом, а не по одному лишь факту
 * исключения: экраны разбирают именно код, и подмена {@code HOST_ONLY} на
 * {@code ROOM_MEMBER_ONLY} — это другая ошибка на экране игрока, хотя дверь
 * закрыта в обоих случаях.
 */
public final class Refusals {

    private Refusals() {
    }

    public static void refuses(String code, int status, ThrowingCallable action) {
        assertThatThrownBy(action)
                .isInstanceOf(ApiException.class)
                .satisfies(thrown -> {
                    ApiException api = (ApiException) thrown;
                    assertThat(api.getCode()).as("код отказа").isEqualTo(code);
                    assertThat(api.getStatus()).as("статус отказа").isEqualTo(status);
                });
    }

    public static void allows(ThrowingCallable action) {
        assertThatCode(action).doesNotThrowAnyException();
    }
}
