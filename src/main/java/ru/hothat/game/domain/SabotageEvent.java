package ru.hothat.game.domain;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * Случившаяся диверсия — то, что рисуют на сцене все участники.
 *
 * <p>Событие самодостаточно: в нём есть и ссылка на мем, и координаты
 * накладки, и длительность. Так сделано, чтобы получатель ничего не
 * дочитывал — событие приезжает и по WebSocket, и в ответе на запрос, и
 * второй источник правды для него завести было бы негде.
 *
 * @param x доля ширины сцены для накладки; {@code null} — оружие без координат
 * @param text реплика «мысли-облака»; есть только у события, которое положил
 *             прежний движок ботов ({@code thought_cloud}), до его переезда
 *             сюда — у игроков облако летит пакетом LiveKit, минуя партию
 * @param voiceId голос озвучки той же реплики, {@code "1"}..{@code "3"};
 *                вместе с {@code text} и только у облака ботов
 */
public record SabotageEvent(String id,
                            String type,
                            String memeId,
                            String clipId,
                            String recordedTurnId,
                            String attackerUid,
                            String attackerName,
                            String targetUid,
                            long createdAtMs,
                            long durationMs,
                            int gameNumber,
                            Double x,
                            Double y,
                            String memeTitle,
                            String memeSrc,
                            String memePoster,
                            String memeMediaPath,
                            String memePosterPath,
                            String memeStorageProvider,
                            // NON_NULL только здесь: обычный выстрел и в пакете LiveKit, и в любом
                            // другом снимке записи должен остаться прежним, без новых null-ключей.
                            @JsonInclude(JsonInclude.Include.NON_NULL) String text,
                            @JsonInclude(JsonInclude.Include.NON_NULL) String voiceId) {

    /** История последних диверсий партии: столько событий помнит комната. */
    public static final int RECENT_LIMIT = 24;

    /** Событие «пошла съёмка Подмены»: у него свой тип и нет боезапаса. */
    public static final String RECORD_TYPE = "replacement_record";

    /**
     * Видно ли событие названному участнику.
     *
     * <p>Выстрел — сцена общая, его видят все за столом и зрители. Съёмку
     * Подмены видят двое: снимающий — чтобы показать себе превью, снимаемый —
     * чтобы включить камеру. Третьему лицу сам факт «кого-то снимают» уже
     * выдаёт заготовку будущей Подмены, поэтому ему событие не отдаётся ни в
     * кадре канала, ни в ответе адреса, ни пакетом LiveKit — правило одно.
     */
    public boolean visibleTo(String viewerUid) {
        if (!RECORD_TYPE.equals(type)) {
            return true;
        }
        return viewerUid != null && (viewerUid.equals(attackerUid) || viewerUid.equals(targetUid));
    }

    /**
     * Кому событие адресовано при рассылке: пусто — всей комнате, иначе
     * только перечисленным. Это то же правило, что {@link #visibleTo}, но в
     * форме списка получателей для канала данных.
     */
    public List<String> audience() {
        if (!RECORD_TYPE.equals(type)) {
            return List.of();
        }
        return Stream.of(attackerUid, targetUid).filter(Objects::nonNull).distinct().toList();
    }

    /** Накладка получает точку на сцене; остальному оружию координаты не нужны. */
    public SabotageEvent withPoint(double x, double y) {
        return new SabotageEvent(id, type, memeId, clipId, recordedTurnId, attackerUid, attackerName, targetUid,
                createdAtMs, durationMs, gameNumber, x, y, memeTitle, memeSrc, memePoster, memeMediaPath,
                memePosterPath, memeStorageProvider, text, voiceId);
    }

    /** Подмена бьёт по снятому игроку, а не по тому, кто объясняет сейчас. */
    public SabotageEvent withTarget(String uid) {
        return new SabotageEvent(id, type, memeId, clipId, recordedTurnId, attackerUid, attackerName, uid,
                createdAtMs, durationMs, gameNumber, x, y, memeTitle, memeSrc, memePoster, memeMediaPath,
                memePosterPath, memeStorageProvider, text, voiceId);
    }
}
