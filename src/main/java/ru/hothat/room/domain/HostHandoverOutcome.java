package ru.hothat.room.domain;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Чем закончился опрос сторожа хозяйства.
 *
 * <p>Отдельным перечислением, а не веткой внутри политики, ровно по одной
 * причине: это же значение уезжает наружу ответом. Сегодня у
 * {@code setup_host_watch} пять несовместимых форм ответа, и клиент различает
 * их по наличию ключей {@code restored}, {@code transferred},
 * {@code privateRoom} и {@code remainingMs} ({@code app-core.js:1082-1084}) —
 * замечание C6 аудита. Одно перечисление снимает и это, и вторую копию
 * названий на стороне DTO.
 *
 * <p>{@code @JsonValue} — аннотация сериализации, не Spring и не база: домен
 * остаётся чистым, а наружу едет строчное имя, привычное фронтенду.
 */
public enum HostHandoverOutcome {

    /** Комната не в наборе: во время партии хозяйство не передают. */
    NOT_APPLICABLE("notApplicable"),
    /** Приватную комнату собирают по ссылке, и хозяина у неё не отнимают. */
    PRIVATE_ROOM("privateRoom"),
    /** Состав неполный: отсчёт бездействия не идёт. */
    WAITING_FOR_PLAYERS("waitingForPlayers"),
    /** Состав полон, время идёт. */
    COUNTDOWN("countdown"),
    /** Прежний хозяин вернулся и получил комнату обратно. */
    RESTORED("restored"),
    /** Время вышло, хозяйство ушло следующему активному участнику. */
    TRANSFERRED("transferred"),
    /** Время вышло, но передавать некому: в комнате остался один хозяин. */
    NO_CANDIDATE("noCandidate");

    private final String wireValue;

    HostHandoverOutcome(String wireValue) {
        this.wireValue = wireValue;
    }

    @JsonValue
    public String wireValue() {
        return wireValue;
    }
}
