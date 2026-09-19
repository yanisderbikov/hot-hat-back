package ru.hothat.recording.port;

import java.util.List;
import java.util.Optional;

/**
 * Комната глазами области записей — ровно то, что попадает в паспорт записи.
 *
 * <p>Область записей не ходит в таблицы комнаты и не знает их формы. Ей нужен
 * снимок: условия партии, состав и счёт на момент съёмки. Именно снимок, а не
 * ссылка, — запись переживает комнату, и её карточка обязана показывать ту
 * партию, которая на видео.
 */
public interface RoomSnapshotPort {

    Optional<RoomSnapshot> find(String roomId);

    /** Сидит ли игрок в комнате: право начать и остановить съёмку — у участника. */
    boolean isMember(String roomId, String uid);

    record RoomSnapshot(String roomId,
                        String name,
                        String phase,
                        int gameNumber,
                        boolean recordingEnabled,
                        String gameMode,
                        boolean ranked,
                        boolean privateRoom,
                        boolean testRoom,
                        String divisionLanguage,
                        String gameLanguage,
                        int wordCount,
                        List<Seat> seats,
                        List<Team> teams) {

        public boolean closed() {
            return "closed".equals(phase);
        }

        public boolean setup() {
            return "setup".equals(phase);
        }
    }

    record Seat(String uid, String nickname, String teamId, boolean bot) {
    }

    record Team(String teamId, String name, String rankedTeamId, int score, List<String> memberUids) {
    }
}
