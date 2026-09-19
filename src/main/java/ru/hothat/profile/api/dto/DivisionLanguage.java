package ru.hothat.profile.api.dto;

import com.fasterxml.jackson.annotation.JsonValue;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Язык дивизиона: он же язык слов в партии, он же ключ рейтинговой таблицы.
 *
 * <p>Сегодня это свободная строка, которую {@code Divisions.normalize} молча
 * подменяет на {@code ru} при любом непонятном значении — опечатка клиента
 * приводила игрока в чужой дивизион без единого сообщения. Здесь набор закрыт:
 * Jackson отвергает чужое значение до входа в контроллер.
 *
 * <p>Список ровно тот, что лежит в {@code ru.hothat.util.Divisions#ALL};
 * второй копии здесь нет — есть перечисление тех же девяти кодов.
 */
@Schema(description = "Код языкового дивизиона")
public enum DivisionLanguage {

    RU("ru"),
    EN("en"),
    DE("de"),
    ES("es"),
    FR("fr"),
    IT("it"),
    ZH("zh"),
    JA("ja"),
    KK("kk");

    private final String wireValue;

    DivisionLanguage(String wireValue) {
        this.wireValue = wireValue;
    }

    @JsonValue
    public String wireValue() {
        return wireValue;
    }

    /**
     * Значение из базы или из старого сервиса. Оно уже прошло
     * {@code Divisions.normalize}, поэтому неизвестного здесь быть не должно;
     * если всё же встретилось — тот же запасной {@link #RU}, что и у нормализатора,
     * иначе чтение профиля падало бы из-за одной кривой строки в таблице.
     */
    public static DivisionLanguage fromWire(String value) {
        for (DivisionLanguage language : values()) {
            if (language.wireValue.equals(value)) {
                return language;
            }
        }
        return RU;
    }
}
