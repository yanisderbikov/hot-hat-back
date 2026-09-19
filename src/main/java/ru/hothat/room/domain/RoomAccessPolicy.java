package ru.hothat.room.domain;

import java.util.Optional;

/**
 * Кого комната впускает игроком, а кого — зрителем.
 *
 * <p>Правила перенесены из браузера: прежде их целиком выполняли
 * {@code joinRoom()} и {@code joinAsSpectator()} в {@code app-core.js}. Это
 * значит, что они были уговором: клиент читал документ комнаты, сам сверял
 * дивизион, сам считал места и сам решал, писать ли себе место игрока. Тот,
 * кто не захотел бы их выполнять, писал место напрямую — прежний документный
 * шлюз путь {@code rooms/*} на запись не проверял (находка A1 аудита). Здесь
 * тот же отбор делает сервер, и обойти его нечем.
 *
 * <p>Класс без Spring и без базы: он принимает готовые признаки, а не строку
 * комнаты, поэтому «правило знает про сущность соседа» здесь не
 * компилируется, а проверить его можно без поднятого приложения.
 */
public final class RoomAccessPolicy {

    private RoomAccessPolicy() {
    }

    /**
     * Признаки комнаты, от которых зависит допуск.
     *
     * <p>Отдельная запись, а не восемь аргументов подряд: четыре булевых
     * значения в вызове перепутать местами нельзя заметить, а по имени поля —
     * можно.
     */
    public record RoomFacts(RoomPhase phase,
                            boolean closed,
                            boolean privateRoom,
                            boolean ranked,
                            boolean managedMatchmaking,
                            String divisionLanguage,
                            String gameLanguage,
                            int capacity) {
    }

    /**
     * Признаки того, кто просится внутрь.
     *
     * @param divisionLanguage   дивизион из карточки игрока
     * @param seatExists         у него уже есть строка места в этой комнате
     * @param frozenParticipant  он числится в замороженном составе идущей партии
     * @param activePlayers      сколько живых игроков в комнате помимо него
     */
    public record Applicant(String divisionLanguage,
                            boolean seatExists,
                            boolean frozenParticipant,
                            int activePlayers) {
    }

    /**
     * Почему не пустили. Каждое значение — свой код ошибки и свой текст:
     * человеку, попавшему не в свой дивизион, и человеку, опоздавшему к
     * началу партии, нужно сказать разное.
     */
    public enum Refusal {
        /** Комната закрыта хозяином, админом или техзавершением. */
        ROOM_CLOSED,
        /** Рейтинговая партия играется только своим дивизионом. */
        RANKED_DIVISION_MISMATCH,
        /** Открытая комната относится к другому языковому дивизиону. */
        ROOM_DIVISION_MISMATCH,
        /** Мест больше нет. */
        ROOM_FULL,
        /** Партия уже идёт, а новичок в замороженный состав не входит. */
        GAME_ALREADY_STARTED,
        /** В приватную идущую партию возвращают только по сохранённому месту. */
        PRIVATE_GAME_STARTED,
        /** Смотреть можно только чужую публичную комнату. */
        PRIVATE_ROOM_NOT_WATCHABLE,
        /** До начала партии зрителей не бывает: все, кто в комнате, — игроки. */
        SPECTATORS_AFTER_START
    }

    /**
     * Пускать ли игроком.
     *
     * <p>Порядок проверок значим: он и определяет, что человек прочтёт первым.
     * Закрытая комната отвечает раньше дивизиона, потому что чинить дивизион
     * ради закрытой комнаты бессмысленно; дивизион — раньше вместимости,
     * потому что «здесь играют на другом языке» не перестанет быть правдой,
     * когда освободится место.
     */
    public static Optional<Refusal> refuseSeat(RoomFacts room, Applicant applicant) {
        // Запирает вход только идущая партия. Доигранная комната и комната,
        // которую пересобирают, впускают как набор: иначе человек, вышедший
        // после последнего хода, не мог бы вернуться к своим же, а комната,
        // застрявшая в промежуточной фазе, оставалась бы закрытой навсегда.
        if (room.closed()) {
            return Optional.of(Refusal.ROOM_CLOSED);
        }
        Optional<Refusal> division = refuseByDivision(room, applicant.divisionLanguage());
        if (division.isPresent()) {
            return division;
        }
        if (room.phase().isLive()) {
            // Приватная партия возвращает только тех, чьё место уцелело: это
            // и есть разница между «перезагрузил вкладку» и «вышел и передумал».
            if (room.privateRoom() && !applicant.seatExists()) {
                return Optional.of(Refusal.PRIVATE_GAME_STARTED);
            }
            if (!applicant.frozenParticipant()) {
                return Optional.of(Refusal.GAME_ALREADY_STARTED);
            }
            // Вернувшийся участник партии проходит мимо вместимости: его место
            // в составе уже занято, и отказать ему «комната полна» значило бы
            // выбросить человека из его же партии.
            return Optional.empty();
        }
        if (!applicant.seatExists() && applicant.activePlayers() >= room.capacity()) {
            return Optional.of(Refusal.ROOM_FULL);
        }
        return Optional.empty();
    }

    /**
     * Пускать ли зрителем.
     *
     * <p>Три отказа и ни одного про дивизион: смотреть чужую партию можно на
     * любом языке — зритель не садится за стол и ни у кого не отнимает места.
     */
    public static Optional<Refusal> refuseSpectatorSeat(RoomFacts room) {
        if (room.closed()) {
            return Optional.of(Refusal.ROOM_CLOSED);
        }
        if (room.privateRoom()) {
            return Optional.of(Refusal.PRIVATE_ROOM_NOT_WATCHABLE);
        }
        if (room.phase().isSetup()) {
            return Optional.of(Refusal.SPECTATORS_AFTER_START);
        }
        return Optional.empty();
    }

    /**
     * Дивизион.
     *
     * <p>Приватная комната намеренно межъязыковая: её собирают по ссылке из
     * личной переписки, и требовать там общий дивизион значило бы запретить
     * позвать друга, живущего в другой лиге.
     *
     * <p>Быстрая комната смешивает дивизионы только тогда, когда её общий язык
     * — английский: это и есть «международная» комната подбора. Правило
     * дословно то же, что во фронте ({@code internationalEnglish}) и в выдаче
     * видеотокена — три копии одной формулы, из которых здесь остаётся одна.
     *
     * <p>Метод открыт наружу отдельно от {@link #refuseSeat}, потому что у
     * видеотокена спрашивают именно дивизион: место в комнате у просящего уже
     * есть, и мерить ему вместимость заново нечем.
     */
    public static Optional<Refusal> refuseByDivision(RoomFacts room, String applicantDivision) {
        if (room.privateRoom()) {
            return Optional.empty();
        }
        if (room.ranked()) {
            return sameLanguage(applicantDivision, room.divisionLanguage())
                    ? Optional.empty() : Optional.of(Refusal.RANKED_DIVISION_MISMATCH);
        }
        boolean internationalEnglish = room.managedMatchmaking() && "en".equals(room.gameLanguage());
        if (internationalEnglish || sameLanguage(applicantDivision, room.gameLanguage())) {
            return Optional.empty();
        }
        return Optional.of(Refusal.ROOM_DIVISION_MISMATCH);
    }

    private static boolean sameLanguage(String left, String right) {
        return left != null && left.equals(right);
    }
}
