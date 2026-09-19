package ru.hothat.lobby.api.dto;

import com.fasterxml.jackson.annotation.JsonValue;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.Optional;

/**
 * Фаза комнаты, которую видно из лобби.
 *
 * <p>Здесь перечислены только живые фазы — те шесть, по которым
 * {@code home/home.js:93} и подписывался на коллекцию комнат. {@code finished}
 * и {@code closed} в перечисление не входят: доигранной и закрытой комнаты в
 * витрине нет вовсе, поэтому «фаза, которую нельзя показать» здесь просто
 * непредставима.
 *
 * <p>Это же перечисление задаёт и запрос: витрина спрашивает хранилище по
 * каждой из этих фаз, а не по списку, вычисленному где-то отдельно. Пока
 * набор жил строкой в клиенте, а фильтр — в браузере, добавление седьмой фазы
 * означало правку в двух местах, и одно из них рано или поздно отстало бы.
 */
@Schema(description = "Фаза комнаты")
public enum LobbyRoomPhase {

    /** Комната собирается: игроки занимают места, партия ещё не началась. */
    SETUP("setup"),
    /** Заставка перед ходом: пара уже названа, время ещё не пошло. */
    TURN_INTRO("turnIntro"),
    /** Идёт ход: объясняющий показывает слово. */
    ACTIVE("active"),
    /** Апелляция: команда оспаривает засчитанные слова. */
    APPEAL("appeal"),
    /** Пауза между ходами. */
    BETWEEN("between"),
    /** Комнату пересобирают на следующую партию. */
    RESETTING("resetting");

    private final String wireValue;

    LobbyRoomPhase(String wireValue) {
        this.wireValue = wireValue;
    }

    @JsonValue
    public String wireValue() {
        return wireValue;
    }

    /**
     * Фаза из базы, если она живая.
     *
     * <p>Пусто — значит комната доиграна, закрыта или у неё в колонке лежит
     * что-то незнакомое. Все три случая для витрины одинаковы: показывать
     * нечего. Отсюда {@link Optional}, а не запасное значение: подставить
     * сюда {@code SETUP} значило бы позвать игрока в комнату, которой нет.
     */
    public static Optional<LobbyRoomPhase> live(String value) {
        for (LobbyRoomPhase phase : values()) {
            if (phase.wireValue.equals(value)) {
                return Optional.of(phase);
            }
        }
        return Optional.empty();
    }
}
