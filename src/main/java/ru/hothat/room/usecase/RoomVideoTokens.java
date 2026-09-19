package ru.hothat.room.usecase;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import ru.hothat.config.ApiException;
import ru.hothat.config.HotHatUser;
import ru.hothat.model.room.Room;
import ru.hothat.model.room.RoomPlayer;
import ru.hothat.model.room.RoomSpectator;
import ru.hothat.profile.spi.PlayerCardPort;
import ru.hothat.repository.GetterRoom;
import ru.hothat.room.api.dto.TurnCredentialsView;
import ru.hothat.room.domain.RoomAccessPolicy;
import ru.hothat.room.domain.RoomPhase;
import ru.hothat.room.port.VideoSessionPort;
import ru.hothat.util.Divisions;
import ru.hothat.util.Ids;
import ru.hothat.util.Json;

import java.util.Optional;

/**
 * Кого пускать в видеосвязь комнаты и с какими правами.
 *
 * <p>Переезд {@code TokenServiceImpl}. Раньше выдача токена была отдельным
 * движком со своей копией допуска: он заново решал, тот ли это дивизион,
 * приватна ли комната и можно ли смотреть, — и копия успела разойтись с той,
 * что применяется на входе в комнату. Здесь решение одно: дивизион считает
 * {@link RoomAccessPolicy}, тот же, что впускает игрока за стол.
 *
 * <p>Подпись токена сюда не переехала и не должна: она принадлежит внешней
 * системе и живёт за {@link VideoSessionPort}. Здесь остаётся отбор — кто
 * участник, под каким именем и с правом ли показывать себя.
 *
 * <p>Поле {@code room_name}, которое браузер присылал, а сервер не читал,
 * исчезло вместе с движком: пока оно ездит, строгий разбор тела включить
 * нельзя (§10.2 плана).
 */
@Component
@RequiredArgsConstructor
public class RoomVideoTokens {

    /** Столько знаков имени помещается на плашке под видео. */
    private static final int MAX_NAME_LENGTH = 40;

    private static final String PLACEHOLDER_PLAYER = "Игрок";
    private static final String PLACEHOLDER_SPECTATOR = "Зритель";

    /** Так подписан зритель, пришедший с главной без своей сессии превью. */
    private static final String DEFAULT_PREVIEW_SESSION = "home";

    private final GetterRoom getterRoom;
    /** Имя и дивизион — у карточки игрока, а не в оболочке учётки. */
    private final PlayerCardPort cards;
    private final VideoSessionPort video;

    /**
     * Выданный токен: адрес сервера, сам токен, под кем вошли и учётка
     * ретранслятора ({@code null} — ретранслятор не настроен).
     *
     * <p>Учётка едет записью порта, а не проекцией ответа: у неё есть поле,
     * которого в контракте нет, — момент истечения. Его читает прежний адрес
     * {@code /api/token}, и выкидывать его здесь значило бы менять форму
     * ответа, который ещё в ходу.
     */
    public record Issued(String serverUrl, String participantToken, String participantIdentity,
                         VideoSessionPort.TurnTicket turn) {
    }

    /** Учётка ретранслятора в форме ответа; {@code null} остаётся {@code null}. */
    public static TurnCredentialsView credentials(Issued issued) {
        VideoSessionPort.TurnTicket turn = issued.turn();
        return turn == null ? null : new TurnCredentialsView(
                turn.urls(), turn.username(), turn.credential(), turn.ttlSeconds());
    }

    /**
     * Токен игрока: с правом показывать себя.
     *
     * @param participantIdentity участник, за которого берут токен; пусто — за себя.
     *                            Не пусто бывает только у администратора своей
     *                            тестовой комнаты: бот не браузер и попросить за
     *                            себя не может
     */
    public Issued forPlayer(HotHatUser user, String roomId, String participantIdentity) {
        String id = Ids.requireRoomId(roomId);
        String identity = participantIdentity == null || participantIdentity.isBlank()
                ? user.uid() : participantIdentity;
        boolean delegated = !identity.equals(user.uid());
        if (delegated && !Ids.TEST_BOT.matcher(identity).matches()) {
            throw ApiException.of("TOKEN_IDENTITY_FORBIDDEN", 403);
        }

        Room room = requireOpenRoom(id);
        Optional<RoomPlayer> seat = getterRoom.getPlayer(id, identity);
        // Порядок отказов тот же, что был: у делегированного запроса первым
        // отвечает «это не твой бот», а не «в комнате никого нет», — иначе по
        // коду ответа можно было бы перебирать чужие тестовые комнаты.
        if (delegated) {
            requireOwnTestBot(user, room, seat.orElse(null));
        } else {
            requireDivision(user, room);
        }
        RoomPlayer player = seat.orElseThrow(() -> ApiException.of("PLAYER_NOT_IN_ROOM", 403));

        String name = displayName(player.getName(), user, PLACEHOLDER_PLAYER);
        return issue(room, identity, name, true, player.getTeamId(), "player", delegated);
    }

    /**
     * Токен зрителя: только на приём.
     *
     * <p>Одна дорога и у зала комнаты, и у превью с главной: различает их
     * только идентификатор сессии, попадающий в имя участника. Сессию называет
     * сервер — раньше её придумывал браузер, и назвавшись чужой сессией можно
     * было выбить из комнаты другого зрителя.
     *
     * @param previewSession сессия превью; пусто — зритель смотрит из самой комнаты
     */
    public Issued forSpectator(HotHatUser user, String roomId, String previewSession) {
        String id = Ids.requireRoomId(roomId);
        Room room = requireOpenRoom(id);
        if (Boolean.TRUE.equals(room.getIsPrivate())) {
            throw ApiException.of("PRIVATE_ROOM_NOT_WATCHABLE", 403);
        }
        RoomSpectator seat = getterRoom.getSpectator(id, user.uid())
                .orElseThrow(() -> ApiException.of("SPECTATOR_NOT_IN_ROOM", 403));

        String identity = "preview-" + user.uid() + "-" + session(previewSession);
        String name = displayName(seat.getName(), user, PLACEHOLDER_SPECTATOR);
        return issue(room, identity, name, false, null, "spectator", false);
    }

    private Room requireOpenRoom(String roomId) {
        Room room = getterRoom.getById(roomId)
                .orElseThrow(() -> ApiException.of("ROOM_NOT_FOUND", 404));
        if (room.isClosed()) {
            // 410, а не 403: комнаты больше нет как площадки, и чинить тут нечего.
            throw ApiException.of("ROOM_CLOSED_BY_ADMIN", 410);
        }
        return room;
    }

    /**
     * Токен за бота берут только в своей тестовой комнате.
     *
     * <p>Три условия подряд, и все три обязательны: админ, его комната, и в ней
     * действительно этот бот. Без последнего админ выписывал бы себе токен на
     * любое имя вида {@code testbot-*} в чужой тестовой комнате.
     */
    private void requireOwnTestBot(HotHatUser user, Room room, RoomPlayer player) {
        if (!user.admin()) {
            throw ApiException.of("ADMIN_REQUIRED", 403);
        }
        String owner = room.getTestOwnerUid() != null && !room.getTestOwnerUid().isBlank()
                ? room.getTestOwnerUid() : Json.str(room.getCreatedBy());
        if (!Boolean.TRUE.equals(room.getIsTestRoom()) || !user.uid().equals(owner)
                || player == null || !Boolean.TRUE.equals(player.getIsTestBot())) {
            throw ApiException.of("TEST_BOT_NOT_IN_ROOM", 403);
        }
    }

    /** Дивизион считает домен — тот же, что впускает игрока за стол. */
    private void requireDivision(HotHatUser user, Room room) {
        RoomAccessPolicy.RoomFacts facts = new RoomAccessPolicy.RoomFacts(
                RoomPhase.fromWire(room.getPhase()),
                room.isClosed(),
                Boolean.TRUE.equals(room.getIsPrivate()),
                Boolean.TRUE.equals(room.getRanked()),
                Boolean.TRUE.equals(room.getManagedMatchmaking()),
                Divisions.normalize(room.getDivisionLanguage()),
                Divisions.normalize(RoomProjections.gameLanguage(room)),
                room.effectiveMaxPlayers());
        String division = Divisions.normalize(cards.card(user.uid())
                .map(PlayerCardPort.Card::divisionLanguage).orElse(null));
        RoomAccessPolicy.refuseByDivision(facts, division).ifPresent(refusal -> {
            throw RoomRefusals.of(refusal);
        });
    }

    private Issued issue(Room room, String identity, String name, boolean publisher,
                         String teamId, String role, boolean testBot) {
        String token = video.participantToken(new VideoSessionPort.Participant(
                room.getId(), identity, name, publisher, publisher ? teamId : null, role, testBot,
                room.getGameNumber() == null ? 0 : room.getGameNumber()));
        return new Issued(video.serverUrl(), token, identity,
                video.turnTicket(identity).orElse(null));
    }

    /**
     * Под каким именем участник виден в видеосвязи.
     *
     * <p>Имя из строки места, потом ник из карточки, потом заглушка. Тела
     * запроса среди источников больше нет: под своим именем в чужую комнату
     * мог сесть кто угодно (находка A1), а плашка под видео — это ровно то
     * место, где такая подмена видна собеседникам.
     */
    private String displayName(String storedInSeat, HotHatUser user, String placeholder) {
        String name = clean(storedInSeat);
        if (!name.isEmpty()) {
            return name;
        }
        name = clean(cards.card(user.uid()).map(PlayerCardPort.Card::nickname).orElse(null));
        return name.isEmpty() ? placeholder : name;
    }

    private static String clean(String value) {
        String trimmed = (value == null ? "" : value).replaceAll("\\s+", " ").trim();
        return trimmed.length() > MAX_NAME_LENGTH ? trimmed.substring(0, MAX_NAME_LENGTH) : trimmed;
    }

    /** Идентификатор сессии превью: только безопасные знаки и не длиннее 32. */
    private static String session(String requested) {
        String safe = Optional.ofNullable(requested).orElse("").replaceAll("[^A-Za-z0-9_-]", "");
        safe = safe.length() > 32 ? safe.substring(0, 32) : safe;
        return safe.isEmpty() ? DEFAULT_PREVIEW_SESSION : safe;
    }
}
