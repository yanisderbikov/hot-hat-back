package ru.hothat.room.usecase;

import org.springframework.stereotype.Component;
import ru.hothat.model.room.Room;
import ru.hothat.model.room.RoomChatMessage;
import ru.hothat.model.room.RoomPlayer;
import ru.hothat.model.room.RoomSpectator;
import ru.hothat.model.room.RoomTeam;
import ru.hothat.profile.api.dto.DivisionLanguage;
import ru.hothat.room.api.dto.RoomChatImageView;
import ru.hothat.room.api.dto.RoomChatMessageView;
import ru.hothat.room.api.dto.RoomSeatKind;
import ru.hothat.room.api.dto.RoomSeatView;
import ru.hothat.room.api.dto.RoomSpectatorView;
import ru.hothat.room.api.dto.RoomSummaryView;
import ru.hothat.room.api.dto.RoomTeamView;
import ru.hothat.room.domain.RoomPhase;
import ru.hothat.room.domain.RoomPresence;
import ru.hothat.team.api.dto.GameMode;
import ru.hothat.util.Divisions;
import ru.hothat.util.Json;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Строки комнаты → проекции, которые видит клиент.
 *
 * <p>Одно место на всю область, потому что одну и ту же комнату отдают
 * одиннадцать сценариев: снимок, вход, выход, создание, переименование,
 * пересборка, жеребьёвка, приглашение и три ответа про команды. Пока каждый
 * собирал ответ у себя, «фаза» приезжала то строкой, то нормализованной, а
 * вместимость — то из {@code maxPlayers}, то из {@code maxParticipants}.
 *
 * <p>Сущности {@code model/**} дальше этого класса не идут: наружу и в
 * сигнатуры сценариев уезжают только записи из {@code api.dto}.
 */
@Component
public class RoomProjections {

    /** Так подписан участник, у которого нет имени ни в комнате, ни в карточке. */
    private static final String PLACEHOLDER_PLAYER = "Игрок";

    /** Так подписан зритель без имени. */
    private static final String PLACEHOLDER_SPECTATOR = "Зритель";

    /** Столько длится ход, если в комнате не записано ничего. */
    private static final int DEFAULT_TURN_SECONDS = 60;

    /** Паспорт комнаты. */
    public RoomSummaryView room(Room room) {
        return new RoomSummaryView(
                room.getId(),
                displayName(room),
                RoomPhase.fromWire(room.getPhase()),
                GameMode.fromWire(room.getGameMode()),
                Boolean.TRUE.equals(room.getRanked()),
                Boolean.TRUE.equals(room.getIsPrivate()),
                Boolean.TRUE.equals(room.getIsTestRoom()),
                DivisionLanguage.fromWire(Divisions.normalize(room.getDivisionLanguage())),
                DivisionLanguage.fromWire(Divisions.normalize(gameLanguage(room))),
                room.effectiveMaxPlayers(),
                // Настройка комнаты, а не длительность текущего хода:
                // turn_duration_seconds — величина одного хода, её ставит
                // партия и сбрасывает возврат к настройкам.
                room.getTurnDuration() == null ? DEFAULT_TURN_SECONDS : room.getTurnDuration(),
                Math.max(0, room.getGameNumber()),
                Boolean.TRUE.equals(room.getRecordGame()),
                Json.str(room.getCreatedBy()),
                room.getHostLastSetupActivityAt() == null ? 0L : Math.max(0L, room.getHostLastSetupActivityAt()),
                room.getCreatedAt() == null ? 0L : room.getCreatedAt().toEpochMilli());
    }

    /**
     * Место игрока.
     *
     * <p>Живость считается здесь, а не в браузере: до сих пор порог в пять
     * минут лежал пятью копиями во фронтенде, и «этот игрок ещё в комнате» мог
     * означать разное на двух соседних экранах.
     */
    public RoomSeatView seat(RoomPlayer player, long nowMs) {
        boolean testBot = Boolean.TRUE.equals(player.getIsTestBot());
        long lastSeenAt = player.getLastSeenAt() == null ? 0L : player.getLastSeenAt();
        return new RoomSeatView(
                player.getUid(),
                name(player.getName(), PLACEHOLDER_PLAYER),
                blankToNull(player.getAvatarDataUrl()),
                blankToNull(player.getTeamId()),
                testBot,
                RoomPresence.playerAlive(testBot, lastSeenAt, nowMs),
                lastSeenAt,
                Boolean.TRUE.equals(player.getCameraEnabled()),
                Boolean.TRUE.equals(player.getMicrophoneEnabled()));
    }

    public List<RoomSeatView> seats(List<RoomPlayer> players, long nowMs) {
        List<RoomSeatView> views = new ArrayList<>(players.size());
        for (RoomPlayer player : players) {
            views.add(seat(player, nowMs));
        }
        return views;
    }

    /** Команда. Порядок задаёт хранилище: выборка уже отсортирована по очереди ходов. */
    public RoomTeamView team(RoomTeam team) {
        return new RoomTeamView(
                team.getTeamId(),
                blankToNull(team.getName()),
                team.getOrder() == null ? 0 : team.getOrder(),
                team.getScore() == null ? 0 : team.getScore(),
                team.getMemberUids() == null ? List.of() : List.copyOf(team.getMemberUids()),
                blankToNull(team.getRankedTeamId()));
    }

    public List<RoomTeamView> teams(List<RoomTeam> teams) {
        List<RoomTeamView> views = new ArrayList<>(teams.size());
        for (RoomTeam team : teams) {
            views.add(team(team));
        }
        return views;
    }

    public RoomSpectatorView spectator(RoomSpectator spectator) {
        return new RoomSpectatorView(
                spectator.getUid(),
                name(spectator.getName(), PLACEHOLDER_SPECTATOR),
                blankToNull(spectator.getAvatarDataUrl()),
                spectator.getLastSeenAt() == null ? 0L : spectator.getLastSeenAt());
    }

    /**
     * Зрители комнаты без лёгких подписок превью.
     *
     * <p>Место с признаком {@code preview} заводит витрина на главной, когда
     * посетитель наводит на карточку комнаты. Такой человек не зритель: он не
     * открывал комнату и через тридцать секунд посмотрит следующую. Считать
     * его в шапке значило бы показывать игрокам зал из случайных прохожих.
     */
    public List<RoomSpectatorView> spectators(List<RoomSpectator> spectators) {
        List<RoomSpectatorView> views = new ArrayList<>();
        for (RoomSpectator spectator : spectators) {
            if (!Boolean.TRUE.equals(spectator.getPreview())) {
                views.add(spectator(spectator));
            }
        }
        return views;
    }

    /**
     * Сообщение чата.
     *
     * <p>Вложение лежит в базе бесформенным {@code jsonb}, потому что писал его
     * браузер. Здесь оно разбирается в одну известную форму, а всё, что на
     * картинку не похоже, отбрасывается: показать «вложение неизвестного вида»
     * экран всё равно не сможет.
     */
    public RoomChatMessageView chatMessage(RoomChatMessage message) {
        return new RoomChatMessageView(
                message.getId(),
                Json.str(message.getUid()),
                name(message.getName(), PLACEHOLDER_PLAYER),
                RoomSeatKind.fromWire(message.getRole()),
                blankToNull(message.getText()),
                image(message.getAttachment()),
                Boolean.TRUE.equals(message.getIsTestBot()),
                message.getCreatedAtMs() == null ? 0L : message.getCreatedAtMs());
    }

    private RoomChatImageView image(Object attachment) {
        Map<String, Object> raw = Json.map(attachment);
        String dataUrl = Json.str(raw.get("dataUrl"));
        if (!"image".equals(Json.str(raw.get("kind"))) || !dataUrl.startsWith("data:image/")) {
            return null;
        }
        return new RoomChatImageView(
                dataUrl,
                (int) Math.max(0, Json.num(raw.get("width"))),
                (int) Math.max(0, Json.num(raw.get("height"))),
                blankToNull(Json.str(raw.get("name"))));
    }

    /**
     * Название комнаты для показа.
     *
     * <p>Безымянную комнату подписывают хвостом идентификатора — так же, как
     * это делает приглашение в переписке. Отдавать пустую строку нельзя:
     * шесть экранов подставили бы туда свои разные заглушки.
     */
    public static String displayName(Room room) {
        String stored = room.getName() == null ? "" : room.getName().trim();
        if (!stored.isEmpty()) {
            return stored;
        }
        String id = room.getId() == null ? "" : room.getId();
        return "Комната " + (id.length() <= 6 ? id : id.substring(id.length() - 6));
    }

    /** Язык слов: у быстрой комнаты он может отличаться от дивизиона. */
    public static String gameLanguage(Room room) {
        if (room.getGameLanguage() != null && !room.getGameLanguage().isBlank()) {
            return room.getGameLanguage();
        }
        if (room.getMatchmakingLanguage() != null && !room.getMatchmakingLanguage().isBlank()) {
            return room.getMatchmakingLanguage();
        }
        return room.getDivisionLanguage();
    }

    private static String name(String stored, String placeholder) {
        return stored == null || stored.isBlank() ? placeholder : stored;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
