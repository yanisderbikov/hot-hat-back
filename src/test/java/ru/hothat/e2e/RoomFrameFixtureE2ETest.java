package ru.hothat.e2e;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import ru.hothat.e2e.support.ApiClient;
import ru.hothat.e2e.support.E2ETest;
import ru.hothat.e2e.support.FixtureRecorder;
import ru.hothat.e2e.support.FixtureRecorder.ViewerCapture;
import ru.hothat.e2e.support.Player;
import ru.hothat.e2e.support.RoomFrameCapture;
import ru.hothat.e2e.support.RoomSteps;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Слепки кадра {@code /ws/v2/room} на одной партии, момент за моментом, —
 * эталон для экрана комнаты, который живёт этим кадром.
 *
 * <p>Золото снято до сноса документного шлюза и лежит в репозитории фронта
 * ({@code test/fixtures/room-frames}: {@code index.json} и {@code moments/}) —
 * там каждая запись несёт и шесть документов прежнего канала, и кадр, снятые
 * одним состоянием. Ту половину больше не пересобрать: шлюза нет. Поэтому
 * этот тест пишет только кадры и только в {@code frames/} — золото он не
 * трогает ни при каком каталоге ({@link FixtureRecorder}).
 *
 * <p>Метод держится на одном свойстве сервера: приветствие и обновление канал
 * комнаты строит одним вызовом сборщика, поэтому свежий сокет под токеном
 * зрителя, открытый после HTTP-действия, отдаёт кадр момента первым же
 * сообщением. Между действием и захватом комнату никто не пишет — уборщики в
 * e2e выключены, heartbeat'ов тест не шлёт.
 *
 * <p>Один {@code @Test} на весь сценарий, как в {@link MatchScenarioE2ETest}:
 * порядок моментов и есть ценность, а правила сервера его диктуют — кик и
 * передача хозяина только до старта, зритель только после, пауза по уходу в
 * e2e без LiveKit не снимается и потому стоит последней в партии.
 *
 * <p>Куда пишутся файлы и в каком виде — {@link FixtureRecorder}. Контракт
 * перевода «кадр → state.*» — {@code docs/api/03-room-channel-adapter.md}.
 */
class RoomFrameFixtureE2ETest extends E2ETest {

    private static final String GENERATOR = "ru.hothat.e2e.RoomFrameFixtureE2ETest";
    private static final String CONTRACT = "hot-hat-back/docs/api/03-room-channel-adapter.md";
    private static final String ROOM_NAME = "Слепки кадров";
    private static final String ROOM_RENAMED = "Слепки кадров · переименована";
    private static final int CAPACITY = 5;
    private static final int TURN_SECONDS = 45;

    /** Слова у каждого свои: шляпа отбрасывает повторы, а нужно двадцать. */
    private static final List<List<String>> WORDS = List.of(
            List.of("Абажур", "Водопад", "Кофемолка", "Полынья", "Скрипка"),
            List.of("Батискаф", "Гербарий", "Дюна", "Ересь", "Жёлудь"),
            List.of("Заноза", "Изразец", "Кальян", "Лебёдка", "Мельница"),
            List.of("Наковальня", "Оазис", "Пряжка", "Ржавчина", "Сургуч"));

    /** PNG 1×1: самое маленькое настоящее изображение, которое примет чат. */
    private static final String DOT_PNG = "data:image/png;base64,"
            + "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNkYPhfDwAChwGA60e6kgAAAABJRU5ErkJggg==";

    /**
     * Живость места считается от {@code lastSeenAt} с окном пять минут
     * ({@code RoomPresence.PLAYER_WINDOW_MS}), а heartbeat'ов тест не шлёт.
     * Если сценарий не уложился в четыре, поздние слепки покажут живых игроков
     * погасшими — такое золото хуже никакого.
     */
    private static final long BUDGET_MS = 4 * 60_000;

    private static final ObjectMapper JSON = new ObjectMapper();

    private RoomSteps steps;
    private FixtureRecorder recorder;
    private boolean captureHttp;
    private String roomId;
    private long startedAtMs;

    /**
     * Зритель слепка: чьим сокетом снимаем и как его подписать.
     *
     * @param seat      {@code player}, {@code spectator} либо {@code outsider}
     * @param roles     роли в этот момент — только подпись для Node-теста
     */
    private record Viewer(Player player, String seat, List<String> roles) {

        static Viewer player(Player player, String... roles) {
            return new Viewer(player, "player", List.of(roles));
        }

        static Viewer spectator(Player player, String... roles) {
            return new Viewer(player, "spectator", List.of(roles));
        }

        /** Выгнанный или вышедший: его экран ещё держит подписки, а место уже отобрано. */
        static Viewer outsider(Player player, String... roles) {
            return new Viewer(player, "outsider", List.of(roles));
        }

    }

    /** Снятый момент: кадры по зрителям — для якорных проверок. */
    private record Moment(String name, List<ViewerCapture> captures) {

        JsonNode frame(Player viewer) {
            return of(viewer).frame();
        }

        JsonNode room(Player viewer) {
            return frame(viewer).path("room");
        }

        private ViewerCapture of(Player viewer) {
            return captures.stream()
                    .filter(capture -> capture.viewer().uid().equals(viewer.uid()))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("в моменте " + name + " нет зрителя " + viewer.nickname()));
        }
    }

    @Test
    @DisplayName("Партия шестерых снимается по моментам кадрами канала комнаты в файлы слепков")
    void captureRoomMoments() throws Exception {
        steps = new RoomSteps(api);
        recorder = FixtureRecorder.open();
        captureHttp = Boolean.parseBoolean(System.getProperty("hothat.fixtures.http", "false"));

        Player anya = registerArmed("Anya");
        Player boris = registerArmed("Boris");
        Player vera = registerArmed("Vera");
        Player gleb = registerArmed("Gleb");
        Player dima = registerArmed("Dima");
        Player olga = registerArmed("Olga");
        startedAtMs = System.currentTimeMillis();

        // ── 01: пустая комната в режиме диверсий — иначе Подмену не снять ──
        roomId = steps.createRoom(anya, ROOM_NAME, CAPACITY, "sabotage");
        Moment created = capture("room-created", "Комната создана: хозяин один за столом, партия в setup",
                Viewer.player(anya, "host"));
        assertThat(created.room(anya).at("/match/match/phase").asText()).isEqualTo("setup");
        assertThat(created.room(anya).at("/snapshot/room/capacity").asInt()).isEqualTo(CAPACITY);
        assertThat(created.room(anya).at("/snapshot/room/gameMode").asText()).isEqualTo("sabotage");
        assertThat(created.room(anya).at("/match/ammo")).hasSize(1);

        // ── 02: трое входят ──
        steps.enter(roomId, boris);
        steps.enter(roomId, vera);
        steps.enter(roomId, gleb);
        Moment entered = capture("three-entered", "Вошли Борис, Вера и Глеб: четыре места без команд",
                Viewer.player(anya, "host"), Viewer.player(vera));
        assertThat(uids(entered.room(vera).at("/snapshot/players")))
                .containsExactly(anya.uid(), boris.uid(), vera.uid(), gleb.uid());

        // ── 03: настройки комнаты и устройства ──
        ApiClient.Response renamed = api.put("/api/v2/room/" + roomId + "/name", anya.token(),
                Map.of("name", ROOM_RENAMED));
        assertThat(renamed.status()).as(renamed.raw()).isEqualTo(200);
        // 45, а не 60: иначе настройку комнаты не отличить от длительности хода.
        ApiClient.Response duration = api.put("/api/v2/room/" + roomId + "/turn-duration", anya.token(),
                Map.of("seconds", TURN_SECONDS));
        assertThat(duration.status()).as(duration.raw()).isEqualTo(200);
        ApiClient.Response devices = api.put("/api/v2/room/" + roomId + "/players/me/devices", vera.token(),
                Map.of("cameraEnabled", true, "microphoneEnabled", false));
        assertThat(devices.status()).as(devices.raw()).isEqualTo(200);
        Moment settings = capture("room-settings",
                "Комната переименована, ход 45 секунд, у Веры включена камера и выключен микрофон",
                Viewer.player(anya, "host"), Viewer.player(vera));
        assertThat(settings.room(anya).at("/snapshot/room/name").asText()).isEqualTo(ROOM_RENAMED);
        assertThat(settings.room(anya).at("/snapshot/room/turnDurationSeconds").asInt()).isEqualTo(TURN_SECONDS);
        assertThat(seatOf(settings.room(anya), vera.uid()).path("cameraEnabled").asBoolean()).isTrue();

        // ── 04: команды и посадка ──
        String red = steps.createTeam(roomId, anya, "Красные");
        String blue = steps.createTeam(roomId, anya, "Синие");
        steps.joinTeam(roomId, anya, red);
        steps.joinTeam(roomId, boris, red);
        steps.joinTeam(roomId, vera, blue);
        steps.joinTeam(roomId, gleb, blue);
        Moment seated = capture("teams-seated", "Две команды по двое: Аня и Борис — красные, Вера и Глеб — синие",
                Viewer.player(anya, "host"), Viewer.player(vera));
        assertThat(seated.room(anya).at("/snapshot/teams")).hasSize(2);
        assertThat(seatOf(seated.room(anya), anya.uid()).path("teamId").asText()).isEqualTo(red);
        assertThat(seatOf(seated.room(anya), vera.uid()).path("teamId").asText()).isEqualTo(blue);

        // ── 05–06: пятый вошёл и выгнан; выгнанный смотрит комнату посторонним ──
        steps.enter(roomId, dima);
        Moment fifth = capture("fifth-entered", "Пятый игрок вошёл: комната заполнена до потолка",
                Viewer.player(anya, "host"), Viewer.player(vera));
        assertThat(fifth.room(anya).at("/snapshot/players")).hasSize(CAPACITY);

        ApiClient.Response kicked = api.send("DELETE", "/api/v2/room/" + roomId + "/players/" + dima.uid(),
                anya.token(), null, Map.of());
        assertThat(kicked.status()).as(kicked.raw()).isEqualTo(204);
        Moment ejected = capture("fifth-kicked",
                "Хозяин выгнал пятого: место пропало, а выгнанному канал отвечает отказом",
                Viewer.player(anya, "host"), Viewer.player(vera), Viewer.outsider(dima, "kicked"));
        assertThat(uids(ejected.room(anya).at("/snapshot/players"))).doesNotContain(dima.uid());
        assertThat(ejected.frame(dima).path("type").asText()).as(ejected.frame(dima).toString()).isEqualTo("error");
        assertThat(ejected.frame(dima).path("code").asText()).isEqualTo("ROOM_MEMBER_ONLY");

        // ── 07: хозяин сменился ──
        ApiClient.Response transferred = api.put("/api/v2/room/" + roomId + "/host", anya.token(),
                Map.of("targetUid", boris.uid()));
        assertThat(transferred.status()).as(transferred.raw()).isEqualTo(200);
        Moment handedOver = capture("host-transferred", "Аня передала комнату Борису",
                Viewer.player(anya), Viewer.player(vera));
        assertThat(handedOver.room(vera).at("/snapshot/room/hostUid").asText()).isEqualTo(boris.uid());

        // ── 08: чат до старта ──
        String anyaMessageId = steps.postChat(roomId, anya, "Всем привет! Скидывайте слова, начинаем через минуту");
        String veraMessageId = steps.postChat(roomId, vera, "Готова, слова уже придумала");
        Moment chatted = capture("chat-text-setup", "Два текстовых сообщения игроков до старта",
                Viewer.player(anya), Viewer.player(vera));
        assertThat(messageIds(chatted.room(anya).at("/chat/items"))).containsExactly(anyaMessageId, veraMessageId);

        // ── 09–10: слова ──
        steps.submitWords(roomId, anya, WORDS.get(0));
        Moment partial = capture("words-partial", "Слова сдала одна Аня: у неё пачка из пяти, у Веры пусто",
                Viewer.player(anya), Viewer.player(vera));
        assertThat(partial.room(anya).at("/words/mine").asInt()).isEqualTo(5);
        assertThat(partial.room(vera).at("/words/mine").asInt()).isZero();
        assertThat(partial.room(vera).at("/words/total").asInt()).isEqualTo(5);

        steps.submitWords(roomId, boris, WORDS.get(1));
        steps.submitWords(roomId, vera, WORDS.get(2));
        steps.submitWords(roomId, gleb, WORDS.get(3));
        Moment submitted = capture("words-submitted", "Слова сдали все четверо: в шляпе двадцать",
                Viewer.player(anya), Viewer.player(vera));
        assertThat(submitted.room(vera).at("/words/total").asInt()).isEqualTo(20);
        assertThat(submitted.room(vera).at("/words/mine").asInt()).isEqualTo(5);

        // ── 11: старт партии — хозяин теперь Борис ──
        ApiClient.Response started = api.post("/api/v2/game/" + roomId, boris.token(), Map.of("intent", "FIRST_GAME"));
        assertThat(started.status()).as(started.raw()).isEqualTo(201);
        String firstTeam = started.text("/match/currentTeamId");
        List<String> firstRoster = strings(started.at("/match/rosters/" + firstTeam));
        // Роли зрителей решает партия: кто из Ани и Веры в активном составе,
        // тот и начнёт ход и станет объясняющим; второй — соперник и стрелок.
        Player explainer1 = firstRoster.contains(anya.uid()) ? anya : vera;
        Player rival1 = explainer1 == anya ? vera : anya;
        assertThat(firstRoster).as("одна из зрительниц играет в первом составе").contains(explainer1.uid());
        Moment matchStarted = capture("match-started", "Партия началась: turnIntro, составы заморожены, хода ещё нет",
                Viewer.player(anya), Viewer.player(vera));
        assertThat(matchStarted.room(anya).at("/match/match/phase").asText()).isEqualTo("turnIntro");
        assertThat(matchStarted.room(anya).at("/match/match/gameNumber").asInt()).isEqualTo(1);
        assertThat(matchStarted.room(anya).at("/match/match/turn").isNull()).isTrue();
        assertThat(strings(matchStarted.room(anya).at("/match/arsenal/loadout"))).containsExactlyElementsOf(LOADOUT);

        // ── 12–15: зритель, чат зрителя, картинка, правка ──
        steps.watch(roomId, olga);
        Moment watching = capture("spectator-seated", "Ольга села зрителем: место зрителя, без снаряжения и слов",
                Viewer.player(anya), Viewer.player(vera), Viewer.spectator(olga));
        assertThat(watching.room(olga).at("/snapshot/viewerSeat").asText()).isEqualTo("spectator");
        assertThat(watching.room(olga).at("/match/arsenal").isNull()).isTrue();
        assertThat(watching.room(olga).at("/words/words")).isEmpty();
        assertThat(watching.room(olga).at("/words/total").asInt()).isEqualTo(20);

        String olgaMessageId = steps.postChat(roomId, olga, "Смотрю за вас, болею за обе команды");
        Moment spectatorChat = capture("chat-spectator", "Сообщение зрителя: место в чате — spectator",
                Viewer.player(anya), Viewer.player(vera), Viewer.spectator(olga));
        assertThat(lastItem(spectatorChat.room(anya).at("/chat/items")).path("seat").asText()).isEqualTo("spectator");

        ApiClient.Response image = api.post("/api/v2/room/" + roomId + "/chat-images", anya.token(),
                Map.of("dataUrl", DOT_PNG, "width", 1, "height", 1, "fileName", "dot.png"));
        assertThat(image.status()).as(image.raw()).isEqualTo(201);
        String imageMessageId = image.text("/message/messageId");
        Moment imaged = capture("chat-image", "Картинка в чате: image в кадре против attachment в документе",
                Viewer.player(anya), Viewer.player(vera), Viewer.spectator(olga));
        assertThat(lastItem(imaged.room(vera).at("/chat/items")).path("image").isObject()).isTrue();

        ApiClient.Response edited = api.send("PATCH", "/api/v2/room/" + roomId + "/chat-messages/" + anyaMessageId,
                anya.token(), Map.of("text", "Всем привет! Слова уже в шляпе — начинаем"), Map.of());
        assertThat(edited.status()).as(edited.raw()).isEqualTo(200);
        Moment editedMoment = capture("chat-edited", "Аня поправила своё первое сообщение",
                Viewer.player(anya), Viewer.player(vera), Viewer.spectator(olga));
        assertThat(itemById(editedMoment.room(olga).at("/chat/items"), anyaMessageId).path("text").asText())
                .isEqualTo("Всем привет! Слова уже в шляпе — начинаем");

        // ── 16: первый ход ──
        ApiClient.Response turn1 = api.post("/api/v2/game/" + roomId + "/turn", explainer1.token(), null);
        assertThat(turn1.status()).as(turn1.raw()).isEqualTo(201);
        String turnId1 = turn1.text("/turn/turnId");
        String firstWord = turn1.text("/turn/currentWord");
        Moment turnStarted = capture("turn-started", "Ход начат: слово у объясняющего, у остальных null",
                Viewer.player(anya, anya == explainer1 ? "explainer" : "rival"),
                Viewer.player(vera, vera == explainer1 ? "explainer" : "rival"),
                Viewer.spectator(olga));
        assertThat(turnStarted.room(explainer1).at("/match/match/phase").asText()).isEqualTo("active");
        assertThat(turnStarted.room(explainer1).at("/match/match/turn/currentWord").asText()).isEqualTo(firstWord);
        assertThat(turnStarted.room(rival1).at("/match/match/turn/currentWord").isNull()).isTrue();
        assertThat(turnStarted.room(olga).at("/match/match/turn/currentWord").isNull()).isTrue();
        // Прежний документ комнаты отдавал слово хода всем троим; кадр сужен на
        // слушателя — в золоте это видно по паре legacy.room.currentWord ↔ frame.
        assertThat(turnStarted.room(rival1).at("/match/match/turn/durationSeconds").asDouble()).isEqualTo(TURN_SECONDS);

        // ── 17–18: угадали и пропустили ──
        ApiClient.Response guessed = api.post("/api/v2/game/" + roomId + "/turn/words/press-0001/guess",
                explainer1.token(), Map.of("turnId", turnId1));
        assertThat(guessed.status()).as(guessed.raw()).isEqualTo(200);
        assertThat(guessed.text("/outcome")).isEqualTo("COUNTED");
        Moment guessedMoment = capture("word-guessed", "Первое слово угадано: очко команде, в шляпе девятнадцать",
                Viewer.player(anya, anya == explainer1 ? "explainer" : "rival"),
                Viewer.player(vera, vera == explainer1 ? "explainer" : "rival"),
                Viewer.spectator(olga));
        assertThat(guessedMoment.room(olga).at("/match/match/turn/score").asInt()).isEqualTo(1);
        assertThat(guessedMoment.room(olga).at("/match/match/wordsLeft").asInt()).isEqualTo(19);
        assertThat(guessedMoment.room(olga).at("/match/match/lastGuessedWord").asText()).isEqualTo(firstWord);

        ApiClient.Response skipped = api.post("/api/v2/game/" + roomId + "/turn/words/press-0002/skip",
                explainer1.token(), Map.of("turnId", turnId1));
        assertThat(skipped.status()).as(skipped.raw()).isEqualTo(200);
        assertThat(skipped.text("/outcome")).isEqualTo("SKIPPED");
        Moment skippedMoment = capture("word-skipped", "Слово пропущено: вернулось в шляпу, счёт не изменился",
                Viewer.player(anya, anya == explainer1 ? "explainer" : "rival"),
                Viewer.player(vera, vera == explainer1 ? "explainer" : "rival"),
                Viewer.spectator(olga));
        assertThat(skippedMoment.room(olga).at("/match/match/wordsLeft").asInt()).isEqualTo(19);
        assertThat(skippedMoment.room(olga).at("/match/match/turn/score").asInt()).isEqualTo(1);

        // ── 19–21: помидор, заказ съёмки Подмены, клип готов ──
        ApiClient.Response shot = api.post("/api/v2/game/" + roomId + "/sabotages", rival1.token(),
                Map.of("type", "tomato"));
        assertThat(shot.status()).as(shot.raw()).isEqualTo(201);
        String tomatoEventId = shot.text("/event/eventId");
        Moment tomato = capture("tomato-fired", "Соперник бросил помидор: событие видят все, счётчик стрелка уменьшился",
                Viewer.player(anya, anya == explainer1 ? "explainer" : "attacker"),
                Viewer.player(vera, vera == explainer1 ? "explainer" : "attacker"),
                Viewer.spectator(olga));
        assertThat(tomato.room(olga).at("/match/match/lastSabotage/eventId").asText()).isEqualTo(tomatoEventId);
        assertThat(ammoOf(tomato.room(explainer1), rival1.uid()).path("ammo").path("tomato").asInt()).isEqualTo(6);

        ApiClient.Response ordered = api.post("/api/v2/game/" + roomId + "/replacement-clips", rival1.token(),
                Map.of("targetUid", explainer1.uid()));
        assertThat(ordered.status()).as(ordered.raw()).isEqualTo(201);
        String clipId = ordered.text("/clip/clipId");
        String replacementEventId = ordered.text("/event/eventId");
        Moment replacement = capture("replacement-ordered",
                "Заказана съёмка Подмены: клип и срок у снимающего, событие у двоих, зритель не видит ничего",
                Viewer.player(anya, anya == explainer1 ? "explainer" : "attacker"),
                Viewer.player(vera, vera == explainer1 ? "explainer" : "attacker"),
                Viewer.spectator(olga));
        assertThat(clipIds(replacement.room(rival1).at("/match/clips"))).containsExactly(clipId);
        assertThat(replacement.room(rival1).at("/match/match/lastSabotage/type").asText()).isEqualTo("replacement_record");
        assertThat(replacement.room(explainer1).at("/match/clips")).isEmpty();
        assertThat(replacement.room(explainer1).at("/match/match/lastSabotage/eventId").asText())
                .isEqualTo(replacementEventId);
        assertThat(replacement.room(olga).at("/match/match/lastSabotage/eventId").asText()).isEqualTo(tomatoEventId);
        assertThat(replacement.frame(olga).toString()).doesNotContain(clipId).doesNotContain("replacement_record");

        // Итог съёмки докладывает снимаемый: плёнку пишет его браузер
        // (ReplacementClipRules.settle сверяет клип с targetUid).
        ApiClient.Response ready = api.put("/api/v2/game/" + roomId + "/replacement-clips/" + clipId + "/result",
                explainer1.token(), Map.of("ready", true));
        assertThat(ready.status()).as(ready.raw()).isEqualTo(200);
        Moment clipReady = capture("clip-ready", "Снимаемый доложил, что клип Подмены снят: ready у клипа заказчика",
                Viewer.player(anya, anya == explainer1 ? "explainer" : "attacker"),
                Viewer.player(vera, vera == explainer1 ? "explainer" : "attacker"),
                Viewer.spectator(olga));
        assertThat(clipReady.room(rival1).at("/match/clips").get(0).path("ready").asBoolean()).isTrue();

        // ── 22–24: ход закрыт, голос, итог ──
        ApiClient.Response completed = api.post("/api/v2/game/" + roomId + "/turn/completion", explainer1.token(),
                Map.of("turnId", turnId1));
        assertThat(completed.status()).as(completed.raw()).isEqualTo(200);
        assertThat(completed.text("/outcome")).isEqualTo("CLOSED");
        Moment appeal = capture("turn-completed", "Ход закрыт объясняющим: апелляция открыта, хода в кадре нет",
                Viewer.player(anya, anya == explainer1 ? "judged" : "juror"),
                Viewer.player(vera, vera == explainer1 ? "judged" : "juror"),
                Viewer.spectator(olga));
        assertThat(appeal.room(olga).at("/match/match/phase").asText()).isEqualTo("appeal");
        assertThat(appeal.room(olga).at("/match/match/turn").isNull()).isTrue();
        assertThat(appeal.room(olga).at("/match/match/appeal/turnId").asText()).isEqualTo(turnId1);
        assertThat(appeal.room(olga).at("/match/match/lastTurn/score").asInt()).isEqualTo(1);

        ApiClient.Response voted = api.put("/api/v2/game/" + roomId + "/appeal/votes/press-0001", rival1.token(),
                Map.of("cancelWord", true));
        assertThat(voted.status()).as(voted.raw()).isEqualTo(200);
        Moment votedMoment = capture("appeal-voted", "Соперник проголосовал за отмену слова: свой голос виден только ему",
                Viewer.player(anya, anya == explainer1 ? "judged" : "juror"),
                Viewer.player(vera, vera == explainer1 ? "judged" : "juror"),
                Viewer.spectator(olga));
        assertThat(votedMoment.room(rival1).at("/match/match/appeal/words").get(0).path("votesToCancel").asInt())
                .isEqualTo(1);
        assertThat(votedMoment.room(rival1).at("/match/match/appeal/words").get(0).path("myVote").asBoolean()).isTrue();
        assertThat(votedMoment.room(explainer1).at("/match/match/appeal/words").get(0).path("myVote").asBoolean())
                .isFalse();

        ApiClient.Response settled = awaitOutcome("итог первой апелляции",
                () -> api.post("/api/v2/game/" + roomId + "/appeal/closing", boris.token(), Map.of("turnId", turnId1)),
                "SETTLED");
        assertThat(settled.at("/matchFinished").asBoolean()).isFalse();
        Moment settledMoment = capture("appeal-settled",
                "Итог подведён: очередь у другой команды, у прошлого хода итоговый счёт",
                Viewer.player(anya), Viewer.player(vera), Viewer.spectator(olga));
        assertThat(settledMoment.room(olga).at("/match/match/phase").asText()).isEqualTo("turnIntro");
        String secondTeam = settledMoment.room(olga).at("/match/match/currentTeamId").asText();
        assertThat(secondTeam).isNotEqualTo(firstTeam);
        assertThat(settledMoment.room(olga).at("/match/match/lastTurn/finalScore").asInt()).isEqualTo(1);

        // ── 25: второй ход — роли зрительниц поменялись местами ──
        Player explainer2 = rival1;
        Player rival2 = explainer1;
        assertThat(strings(settledMoment.room(olga).at("/match/match/rosters/" + secondTeam)))
                .as("во втором составе играет вторая зрительница").contains(explainer2.uid());
        ApiClient.Response turn2 = api.post("/api/v2/game/" + roomId + "/turn", explainer2.token(), null);
        assertThat(turn2.status()).as(turn2.raw()).isEqualTo(201);
        String turnId2 = turn2.text("/turn/turnId");
        String secondTurnWord = turn2.text("/turn/currentWord");
        Moment secondTurn = capture("second-turn-started", "Второй ход: слово у другой зрительницы",
                Viewer.player(anya, anya == explainer2 ? "explainer" : "rival"),
                Viewer.player(vera, vera == explainer2 ? "explainer" : "rival"),
                Viewer.spectator(olga));
        assertThat(secondTurn.room(explainer2).at("/match/match/turn/currentWord").asText()).isEqualTo(secondTurnWord);
        assertThat(secondTurn.room(rival2).at("/match/match/turn/currentWord").isNull()).isTrue();
        assertThat(secondTurn.room(olga).at("/match/match/lastTurn").isNull()).isTrue();

        // ── 26–27: пауза хозяина и её снятие ──
        ApiClient.Response paused = api.put("/api/v2/game/" + roomId + "/pause", boris.token(), null);
        assertThat(paused.status()).as(paused.raw()).isEqualTo(200);
        Moment pausedMoment = capture("paused-by-host", "Хозяин поставил партию на паузу посреди хода",
                Viewer.player(anya, anya == explainer2 ? "explainer" : "rival"),
                Viewer.player(vera, vera == explainer2 ? "explainer" : "rival"),
                Viewer.spectator(olga));
        assertThat(pausedMoment.room(olga).at("/match/match/pause/paused").asBoolean()).isTrue();
        assertThat(pausedMoment.room(olga).at("/match/match/pause/reason").asText()).isEqualTo("host_paused");
        assertThat(pausedMoment.room(olga).at("/match/match/pause/turnRemainingMs").asLong()).isPositive();
        assertThat(pausedMoment.room(olga).at("/match/match/turn/deadlineMs").asLong()).isZero();

        ApiClient.Response resumed = api.send("DELETE", "/api/v2/game/" + roomId + "/pause", boris.token(),
                null, Map.of());
        assertThat(resumed.status()).as(resumed.raw()).isEqualTo(200);
        assertThat(resumed.at("/resumed").asBoolean()).isTrue();
        Moment resumedMoment = capture("resumed", "Пауза снята: длительность хода равна остатку, а не настройке",
                Viewer.player(anya, anya == explainer2 ? "explainer" : "rival"),
                Viewer.player(vera, vera == explainer2 ? "explainer" : "rival"),
                Viewer.spectator(olga));
        assertThat(resumedMoment.room(olga).at("/match/match/pause/paused").asBoolean()).isFalse();
        assertThat(resumedMoment.room(olga).at("/match/match/turn/durationSeconds").asDouble())
                .isLessThan(TURN_SECONDS).isPositive();

        // ── 28: шляпа опустошается угадыванием; последнее слово закрывает ход само ──
        int guessedInSecondTurn = 0;
        boolean closedByLastWord = false;
        for (int press = 101; press <= 199 && !closedByLastWord; press++) {
            ApiClient.Response guess = api.post(
                    "/api/v2/game/" + roomId + "/turn/words/press-" + String.format("%04d", press) + "/guess",
                    explainer2.token(), Map.of("turnId", turnId2));
            assertThat(guess.status()).as(guess.raw()).isEqualTo(200);
            assertThat(guess.text("/outcome")).isEqualTo("COUNTED");
            guessedInSecondTurn++;
            closedByLastWord = guess.at("/turnClosed").asBoolean();
        }
        assertThat(closedByLastWord).as("ход закрылся на последнем слове шляпы").isTrue();
        Moment emptied = capture("hat-emptied", "Шляпа пуста: ход закрылся сам, апелляция по " + guessedInSecondTurn
                        + " словам",
                Viewer.player(anya, anya == explainer2 ? "judged" : "juror"),
                Viewer.player(vera, vera == explainer2 ? "judged" : "juror"),
                Viewer.spectator(olga));
        assertThat(emptied.room(olga).at("/match/match/phase").asText()).isEqualTo("appeal");
        assertThat(emptied.room(olga).at("/match/match/wordsLeft").asInt()).isZero();
        assertThat(emptied.room(olga).at("/match/match/lastTurn/score").asInt()).isEqualTo(guessedInSecondTurn);

        // ── 29: уход игрока после истечения окна голосования ──
        // Ждём конца окна ДО ухода: тогда замороженный остаток апелляции — ноль,
        // и итог можно подвести сквозь паузу (pauseOutlivedAppeal). Иначе пауза
        // по уходу в e2e не снимается вовсе — сверка присутствия требует LiveKit.
        awaitAppealWindowEnd(olga);
        ApiClient.Response departed = api.post("/api/v2/game/" + roomId + "/players/me/departure", gleb.token(), null);
        assertThat(departed.status()).as(departed.raw()).isEqualTo(200);
        assertThat(departed.at("/paused").asBoolean()).isTrue();
        Moment departure = capture("paused-by-departure",
                "Глеб ушёл: пауза по пропавшему игроку, его место погасло",
                Viewer.player(anya), Viewer.player(vera), Viewer.spectator(olga));
        assertThat(departure.room(olga).at("/match/match/pause/reason").asText()).isEqualTo("player_disconnected");
        assertThat(strings(departure.room(olga).at("/match/match/pause/missingUids"))).containsExactly(gleb.uid());
        assertThat(seatOf(departure.room(olga), gleb.uid()).path("alive").asBoolean()).isFalse();
        assertThat(seatOf(departure.room(olga), gleb.uid()).path("lastSeenAtMs").asLong()).isZero();

        // ── 30: партия сыграна ──
        ApiClient.Response finished = awaitOutcome("итог второй апелляции",
                () -> api.post("/api/v2/game/" + roomId + "/appeal/closing", anya.token(), Map.of("turnId", turnId2)),
                "SETTLED");
        assertThat(finished.at("/matchFinished").asBoolean()).isTrue();
        Moment finishedMoment = capture("match-finished", "Партия сыграна: шляпа пуста, пауза снята финишем",
                Viewer.player(anya), Viewer.player(vera), Viewer.spectator(olga));
        assertThat(finishedMoment.room(olga).at("/match/match/phase").asText()).isEqualTo("finished");
        assertThat(finishedMoment.room(olga).at("/match/match/pause/paused").asBoolean()).isFalse();
        assertThat(finishedMoment.room(olga).at("/match/match/termination").isNull()).isTrue();
        assertThat(finishedMoment.room(olga).at("/match/match/lastTurn/finalScore").asInt())
                .isEqualTo(guessedInSecondTurn);

        // ── 31: комната пересобрана тем же составом ──
        ApiClient.Response reset = api.post("/api/v2/room/" + roomId + "/reset", boris.token(),
                Map.of("clearTeams", false));
        assertThat(reset.status()).as(reset.raw()).isEqualTo(200);
        Moment resetMoment = capture("room-reset", "Комната снова в setup: команды на месте, счёт обнулён",
                Viewer.player(anya), Viewer.player(vera), Viewer.spectator(olga));
        assertThat(resetMoment.room(anya).at("/match/match/phase").asText()).isEqualTo("setup");
        assertThat(resetMoment.room(anya).at("/snapshot/teams")).hasSize(2);
        assertThat(resetMoment.room(anya).at("/snapshot/teams").get(0).path("score").asInt()).isZero();

        // ── 32: окно чата переполнено ──
        for (int i = 1; i <= 45; i++) {
            steps.postChat(roomId, anya, "Сообщение № " + i + " для переполнения окна");
        }
        Moment overflow = capture("chat-window-overflow", "В чате больше сорока сообщений: окно и курсор вглубь",
                Viewer.player(anya), Viewer.player(vera), Viewer.spectator(olga));
        assertThat(overflow.room(olga).at("/chat/items")).hasSize(40);
        assertThat(overflow.room(olga).at("/chat/nextCursor").isNull()).as("курсор вглубь").isFalse();

        // ── 33: игрок вышел; вышедший смотрит посторонним ──
        ApiClient.Response left = api.send("DELETE", "/api/v2/room/" + roomId + "/players/me", gleb.token(),
                null, Map.of());
        assertThat(left.status()).as(left.raw()).isEqualTo(200);
        Moment leftMoment = capture("player-left", "Глеб вышел из комнаты: место и членство в команде пропали",
                Viewer.player(anya), Viewer.player(vera), Viewer.spectator(olga), Viewer.outsider(gleb, "left"));
        assertThat(uids(leftMoment.room(anya).at("/snapshot/players"))).doesNotContain(gleb.uid());
        assertThat(teamOf(leftMoment.room(anya), blue).path("memberUids").toString()).doesNotContain(gleb.uid());
        assertThat(leftMoment.frame(gleb).path("type").asText()).isEqualTo("error");

        // ── 34: хозяин закрыл комнату при живых соседях — строка остаётся ──
        ApiClient.Response closed = api.send("DELETE", "/api/v2/room/" + roomId, boris.token(), null, Map.of());
        assertThat(closed.status()).as(closed.raw()).isEqualTo(200);
        assertThat(closed.at("/deleted").asBoolean()).as("остальные живы — комната помечена, а не стёрта").isFalse();
        Moment closedMoment = capture("room-closed", "Хозяин закрыл комнату: phase=closed, экран уходит",
                Viewer.player(anya), Viewer.player(vera), Viewer.spectator(olga));
        JsonNode closedFrame = closedMoment.frame(anya);
        if ("error".equals(closedFrame.path("type").asText())) {
            // Канал на закрытой комнате отказал — сам отказ и есть слепок;
            // экран трактует его как «уйти с экрана».
            assertThat(closedFrame.path("code").asText()).isNotBlank();
        } else {
            assertThat(closedMoment.room(anya).at("/snapshot/room/phase").asText()).isEqualTo("closed");
        }

        long elapsedMs = System.currentTimeMillis() - startedAtMs;
        assertThat(elapsedMs).as("сценарий должен уложиться в окно живости мест").isLessThan(BUDGET_MS);

        // ── индекс ──
        ObjectNode room = JSON.createObjectNode();
        room.put("roomId", roomId);
        room.put("name", ROOM_RENAMED);
        room.put("gameMode", "sabotage");
        room.put("capacity", CAPACITY);
        room.put("turnDurationSeconds", TURN_SECONDS);
        room.put("elapsedMs", elapsedMs);

        ObjectNode viewers = JSON.createObjectNode();
        Map<Player, String> teamsByPlayer = new LinkedHashMap<>();
        teamsByPlayer.put(anya, red);
        teamsByPlayer.put(boris, red);
        teamsByPlayer.put(vera, blue);
        teamsByPlayer.put(gleb, blue);
        teamsByPlayer.put(dima, null);
        teamsByPlayer.put(olga, null);
        teamsByPlayer.forEach((player, teamId) -> {
            ObjectNode node = viewers.putObject(player.uid());
            node.put("nickname", player.nickname());
            if (teamId == null) {
                node.putNull("team");
            } else {
                node.put("team", teamId);
            }
            node.put("seat", player == olga ? "spectator" : "player");
        });

        ObjectNode legend = JSON.createObjectNode();
        ObjectNode teams = legend.putObject("teams");
        teams.put(red, "Красные");
        teams.put(blue, "Синие");
        legend.put("hostAtCreation", anya.uid());
        legend.put("hostAfterTransfer", boris.uid());
        legend.put("kickedUid", dima.uid());
        legend.put("departedUid", gleb.uid());
        legend.put("leftUid", gleb.uid());
        legend.put("spectatorUid", olga.uid());
        ArrayNode turnIds = legend.putArray("turnIds");
        turnIds.add(turnId1);
        turnIds.add(turnId2);
        ObjectNode explainers = legend.putObject("explainers");
        explainers.put(turnId1, explainer1.uid());
        explainers.put(turnId2, explainer2.uid());
        legend.put("firstTeamId", firstTeam);
        legend.put("secondTeamId", secondTeam);
        legend.put("attackerUid", rival1.uid());
        legend.put("tomatoEventId", tomatoEventId);
        legend.put("clipId", clipId);
        legend.put("replacementEventId", replacementEventId);
        ObjectNode messages = legend.putObject("messageIds");
        messages.put("anyaFirst", anyaMessageId);
        messages.put("veraFirst", veraMessageId);
        messages.put("spectator", olgaMessageId);
        messages.put("image", imageMessageId);
        messages.put("edited", anyaMessageId);
        ObjectNode presses = legend.putObject("pressIds");
        presses.put("guessed", "press-0001");
        presses.put("skipped", "press-0002");
        presses.put("secondTurnFirst", "press-0101");
        presses.put("secondTurnLast", "press-" + String.format("%04d", 100 + guessedInSecondTurn));
        legend.put("secondTurnGuessed", guessedInSecondTurn);
        legend.put("chatOverflowMessages", 45);
        legend.put("chatWindow", 40);
        legend.put("presenceWindowMs", 5 * 60_000);

        Path index = recorder.finish(GENERATOR, CONTRACT, room, viewers, legend, List.of(
                "between: фаза BETWEEN сервером не выставляется (только advance() её ждёт); «между ходами» = appeal и turnIntro с lastTurn",
                "turn expiry: истечение хода по часам (POST /turn/expiry) требует 30+3 с ожидания; итог тот же, что после completion",
                "termination: техническое завершение и снятие паузы по уходу требуют сверки присутствия с LiveKit, недоступной в e2e",
                "ranked=true, testRoom=true, privateRoom=true: рейтинг требует подбора, тестовая комната — админа, приватная не пускает зрителя",
                "recordingRequested=true: recording-preference доступен только роли OWNER",
                "avatarDataUrl: у учёток e2e аватара нет",
                "testBot / testBotIds / isTestBotSubmission: боты только в тестовой комнате"));
        System.out.println("Слепки записаны: " + recorder.root() + " (" + index.getFileName() + ", "
                + elapsedMs + " мс)");
    }

    // ───────────────────────── захват ─────────────────────────

    /**
     * Снять момент под каждым зрителем: приветственный кадр канала свежим
     * сокетом под токеном зрителя. Между HTTP-действием и захватом комнату
     * никто не пишет (уборщики в e2e выключены, heartbeat'ов тест не шлёт),
     * поэтому кадры всех зрителей — одного и того же состояния.
     */
    private Moment capture(String name, String description, Viewer... viewers) throws Exception {
        List<ViewerCapture> captures = new ArrayList<>();
        for (Viewer viewer : viewers) {
            long capturedAtMs = System.currentTimeMillis();
            JsonNode frame = RoomFrameCapture.hello(this, viewer.player(), roomId);
            JsonNode http = captureHttp ? httpBlock(viewer.player()) : null;
            captures.add(new ViewerCapture(viewer.player(), viewer.seat(), viewer.roles(), capturedAtMs,
                    frame, http));
        }
        recorder.record(name, description, captures);
        long elapsedMs = System.currentTimeMillis() - startedAtMs;
        assertThat(elapsedMs).as("момент %s снят за пределами окна живости", name).isLessThan(BUDGET_MS);
        return new Moment(name, captures);
    }

    /**
     * Ответы адресов HTTP тому же зрителю: контракт обещает, что части кадра
     * и ответы адресов кормят один адаптер. Постороннему адреса отвечают
     * отказом — код ответа записывается вместе с телом.
     */
    private JsonNode httpBlock(Player viewer) {
        ObjectNode block = JSON.createObjectNode();
        block.set("room", response(api.get("/api/v2/room/" + roomId, viewer.token())));
        block.set("game", response(api.get("/api/v2/game/" + roomId, viewer.token())));
        block.set("chat", response(api.get("/api/v2/room/" + roomId + "/chat-messages", viewer.token())));
        return block;
    }

    private static ObjectNode response(ApiClient.Response response) {
        ObjectNode node = JSON.createObjectNode();
        node.put("status", response.status());
        node.set("body", response.body());
        return node;
    }

    // ───────────────────────── ожидания ─────────────────────────

    /**
     * Опрашивать действие, пока исход не станет нужным. Апелляция закрывается
     * не раньше чем через десять секунд ({@code TurnRules.APPEAL_MS}), и до
     * того сервер честно отвечает {@code STILL_OPEN}.
     */
    private static ApiClient.Response awaitOutcome(String expectation, Supplier<ApiClient.Response> action,
                                                   String outcome) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 15_000;
        ApiClient.Response last = null;
        while (System.currentTimeMillis() < deadline) {
            last = action.get();
            assertThat(last.status()).as("%s: %s", expectation, last.raw()).isEqualTo(200);
            if (outcome.equals(last.text("/outcome"))) {
                return last;
            }
            Thread.sleep(500);
        }
        throw new AssertionError("Не дождались " + outcome + " (" + expectation + "): "
                + (last == null ? "ни одного ответа" : last.raw()));
    }

    /** Дождаться, когда серверные часы перейдут за конец окна голосования. */
    private void awaitAppealWindowEnd(Player viewer) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 15_000;
        while (System.currentTimeMillis() < deadline) {
            ApiClient.Response state = api.get("/api/v2/game/" + roomId, viewer.token());
            assertThat(state.status()).as(state.raw()).isEqualTo(200);
            long endsAt = state.at("/match/appeal/endsAtMs").asLong();
            long serverTime = state.at("/match/serverTimeMs").asLong();
            assertThat(endsAt).as("апелляция должна быть открыта: %s", state.raw()).isPositive();
            if (serverTime > endsAt) {
                return;
            }
            Thread.sleep(Math.min(500, Math.max(50, endsAt - serverTime + 20)));
        }
        throw new AssertionError("Окно голосования не закончилось за 15 с");
    }

    // ───────────────────────── чтение кадров ─────────────────────────

    private static JsonNode seatOf(JsonNode room, String uid) {
        for (JsonNode seat : room.at("/snapshot/players")) {
            if (uid.equals(seat.path("uid").asText())) {
                return seat;
            }
        }
        throw new AssertionError("в снимке нет места " + uid + ": " + room);
    }

    private static JsonNode teamOf(JsonNode room, String teamId) {
        for (JsonNode team : room.at("/snapshot/teams")) {
            if (teamId.equals(team.path("teamId").asText())) {
                return team;
            }
        }
        throw new AssertionError("в снимке нет команды " + teamId + ": " + room);
    }

    private static JsonNode ammoOf(JsonNode room, String uid) {
        for (JsonNode item : room.at("/match/ammo")) {
            if (uid.equals(item.path("uid").asText())) {
                return item;
            }
        }
        throw new AssertionError("в кадре нет счётчиков игрока " + uid + ": " + room);
    }

    private static JsonNode lastItem(JsonNode items) {
        assertThat(items.size()).as("лента не пуста").isPositive();
        return items.get(items.size() - 1);
    }

    private static JsonNode itemById(JsonNode items, String messageId) {
        for (JsonNode item : items) {
            if (messageId.equals(item.path("messageId").asText())) {
                return item;
            }
        }
        throw new AssertionError("в ленте нет сообщения " + messageId + ": " + items);
    }

    private static List<String> messageIds(JsonNode items) {
        List<String> ids = new ArrayList<>();
        items.forEach(item -> ids.add(item.path("messageId").asText()));
        return ids;
    }

    private static List<String> clipIds(JsonNode clips) {
        List<String> ids = new ArrayList<>();
        clips.forEach(clip -> ids.add(clip.path("clipId").asText()));
        return ids;
    }

    private static List<String> uids(JsonNode seats) {
        List<String> uids = new ArrayList<>();
        seats.forEach(seat -> uids.add(seat.path("uid").asText()));
        return uids;
    }

    private static List<String> strings(JsonNode array) {
        List<String> values = new ArrayList<>();
        array.forEach(node -> values.add(node.asText()));
        return values;
    }
}
