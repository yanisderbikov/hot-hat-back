package ru.hothat.recording.api.dto;

import com.fasterxml.jackson.annotation.JsonValue;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Стадия жизни записи партии.
 *
 * <p>Сегодня это свободная строка в колонке {@code game_recording.status}, и
 * интерфейс восстанавливает смысл сам: {@code portal.js:60-63} считает запись
 * готовой при {@code status === "complete"} и провалившейся при попадании в
 * список {@code ["failed","deleted"]}, ровно те же две проверки повторяет
 * {@code app-core.js:11552}. Два места ветвятся по одним и тем же строкам, и
 * каждое может ошибиться по-своему. Здесь набор закрыт и назван.
 *
 * <p>Рядом со статусом в базе живёт числовой {@code livekit_status} — код
 * Egress. Наружу он не выходит: игроку нечего делать с внутренним кодом
 * чужого сервиса, а сервер и так свёл его к этим шести стадиям.
 */
@Schema(description = "Стадия жизни записи партии")
public enum RecordingStatus {

    /** Задание Egress создано, съёмка ещё не подтверждена. */
    STARTING("starting"),
    /** Egress снимает партию прямо сейчас. */
    ACTIVE("active"),
    /** Съёмка кончилась, файл ещё собирается и выкладывается в хранилище. */
    PROCESSING("processing"),
    /** Файл лежит в хранилище: только в этой стадии есть что смотреть. */
    COMPLETE("complete"),
    /** Запись не получилась; причина — в поле {@code error} карточки. */
    FAILED("failed"),
    /** Файл удалён: истёк срок хранения либо запись убрал администратор. */
    DELETED("deleted");

    private final String wireValue;

    RecordingStatus(String wireValue) {
        this.wireValue = wireValue;
    }

    @JsonValue
    public String wireValue() {
        return wireValue;
    }

    /**
     * Значение из базы. Пустая строка — это момент создания строки записи,
     * старый движок читает её как {@code starting}
     * ({@code RecordingLibraryServiceImpl:126}), и мы повторяем это чтение.
     *
     * <p>Незнакомое значение отдаём как {@link #PROCESSING}: это единственная
     * стадия, которая ничего не обещает — ни готового файла, ни провала, —
     * поэтому клиент просто продолжит опрашивать, а не покажет игроку кнопку
     * «смотреть», за которой ничего нет.
     */
    public static RecordingStatus fromWire(String value) {
        if (value == null || value.isBlank()) {
            return STARTING;
        }
        for (RecordingStatus status : values()) {
            if (status.wireValue.equals(value)) {
                return status;
            }
        }
        return PROCESSING;
    }
}
