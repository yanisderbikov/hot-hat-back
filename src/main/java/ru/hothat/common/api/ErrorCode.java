package ru.hothat.common.api;

import ru.hothat.config.ApiException;

/**
 * Код ошибки, её HTTP-статус и текст для человека — в одном месте.
 *
 * <p>Сегодня код живёт строкой в 326 вызовах {@code ApiException.of("…", 4xx)},
 * а текст к нему — отдельно, в {@link ru.hothat.config.ErrorMessages}. Из-за
 * этого один код приезжает клиенту с разными статусами, а 110 кодов не имеют
 * текста вовсе и показываются пользователю машинной строкой.
 *
 * <p>Перечисление наполняется по мере переезда областей на {@code /api/v2}:
 * здесь ровно те коды, которыми пользуется уже переехавший срез.
 */
public enum ErrorCode {

    AUTH_REQUIRED(401, "Требуется вход в аккаунт."),
    ADMIN_REQUIRED(403, "Нет прав администратора."),
    FEATURE_DISABLED(403, "Возможность выключена."),
    ROOM_NOT_FOUND(404, "Комната не найдена."),
    TEST_ROOM_ONLY(403, "Действие доступно только в тестовой комнате."),
    OWNER_ONLY(403, "Это действие доступно только владельцу HOT-HAT."),
    ROUND_NOT_ACTIVE(409, "Раунд сейчас не идёт."),
    // Консоль администратора: ошибка в идентификаторе записи. Текст тот же,
    // что видит игрок, — запись у них одна и та же.
    RECORDING_NOT_FOUND(404, "Запись не найдена."),
    // Модерация. Собственный код вместо общего USER_NOT_FOUND: администратор
    // ошибается в чужом идентификаторе, и «аккаунт не найден» на форме входа и
    // «некого блокировать» в консоли — разные сообщения разным людям.
    BAN_TARGET_NOT_FOUND(404, "Аккаунт для блокировки не найден."),
    // 422, а не 400: тело запроса безупречно, невыполнимо само требование.
    BAN_SELF_FORBIDDEN(422, "Себя заблокировать нельзя.");

    private final int status;
    private final String text;

    ErrorCode(int status, String text) {
        this.status = status;
        this.text = text;
    }

    public int status() {
        return status;
    }

    public String text() {
        return text;
    }

    /** Готовое исключение: {@code throw ErrorCode.ROOM_NOT_FOUND.raise();} */
    public ApiException raise() {
        return ApiException.of(name(), status);
    }
}
