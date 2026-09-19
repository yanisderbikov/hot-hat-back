package ru.hothat.auth.api.dto;

import com.fasterxml.jackson.annotation.JsonValue;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Род учётной записи: гость или полноценный аккаунт.
 *
 * <p>Заменяет булево поле {@code guest} из старого профиля. Причина не в
 * красоте: булево поле отвечает только на вопрос «гость ли», и когда рядом
 * появится третий род (сервисная учётка рекордера, машинная — §5.13 плана),
 * оно превратится во второе булево поле, а сочетание «guest=true и service=true»
 * станет представимым. Перечисление делает такое сочетание невозможным.
 *
 * <p>Значения на проводе — те же слова, что в плане (§8: строка
 * {@code kind='guest'} в {@code user_account}), поэтому клиент читает их
 * как есть.
 */
@Schema(description = "Род учётной записи")
public enum AccountKind {

    /** Почта и пароль есть; ник занят в индексе; играет во всё. */
    USER("user"),

    /** Ни почты, ни пароля. Открыты лобби, превью комнаты, апгрейд и согласия. */
    GUEST("guest");

    private final String wireValue;

    AccountKind(String wireValue) {
        this.wireValue = wireValue;
    }

    @JsonValue
    public String wireValue() {
        return wireValue;
    }

    /** Род по флагу старого профиля: {@code AppUser.guest}. */
    public static AccountKind of(boolean guest) {
        return guest ? GUEST : USER;
    }
}
