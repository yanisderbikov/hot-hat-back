package ru.hothat.e2e;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import ru.hothat.e2e.support.ApiClient;
import ru.hothat.e2e.support.Channel;
import ru.hothat.e2e.support.E2EDatabase;
import ru.hothat.e2e.support.E2ETest;
import ru.hothat.e2e.support.Frame;
import ru.hothat.e2e.support.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Что несёт кадр канала {@code /ws/v2/room/{roomId}} — и кому.
 *
 * <p>Экран комнаты переезжает с шести подписок старого шлюза на один кадр, и
 * переезд держится на двух обещаниях. Первое: кадр несёт всё, что раньше
 * приезжало подписками, — вошедший игрок, счёт, фаза, свои слова. Второе:
 * кадр собирается на слушателя — слово хода видит только объясняющий, а
 * чужой боезапас приезжает счётчиками, без единого идентификатора мема, а
 * съёмка Подмены — событие для двоих — не приезжает ни соседу по столу, ни
 * зрителю. Второе не видно ни в спецификации, ни в модульных тестах сборщика
 * по отдельности: его проверяют несколько сокетов под разными токенами на
 * одной партии.
 */
class RoomChannelFrameE2ETest extends E2ETest {

    /** Слова у каждого свои: шляпа отбрасывает повторы, а нужно двадцать. */
    private static final List<List<String>> WORDS = List.of(
            List.of("Абажур", "Водопад", "Кофемолка", "Полынья", "Скрипка"),
            List.of("Батискаф", "Гербарий", "Дюна", "Ересь", "Жёлудь"),
            List.of("Заноза", "Изразец", "Кальян", "Лебёдка", "Мельница"),
            List.of("Наковальня", "Оазис", "Пряжка", "Ржавчина", "Сургуч"));

    /**
     * Обойма соперника — из других роликов, чем у всех. Иначе «в кадре нет
     * чужих мемов» не отличить от «свои мемы те же самые».
     */
    private static final List<String> RIVAL_LOADOUT = List.of(
            "builtin-e2e-rival-1", "builtin-e2e-rival-2", "builtin-e2e-rival-3",
            "builtin-e2e-rival-4", "builtin-e2e-rival-5");

    @Test
    @DisplayName("Вошедший игрок появляется в кадре подписчика вместе со счётчиками своего боезапаса")
    void joinShowsUpInSubscriberFrame() throws Exception {
        Player anya = registerArmed("Anya");
        Player boris = registerArmed("Boris");
        Player vera = registerArmed("Vera");

        String roomId = createRoom(anya);
        enter(roomId, boris);

        try (Channel channel = Channel.open(ws("/ws/v2/room/" + roomId, boris))) {
            Frame hello = channel.nextFrame("приветственный кадр Бориса");
            assertThat(hello.type()).as(hello.raw()).isEqualTo("hello");
            assertThat(hello.text("/room/snapshot/viewerSeat")).isEqualTo("player");
            assertThat(uids(hello.at("/room/snapshot/players"))).containsExactlyInAnyOrder(anya.uid(), boris.uid());
            assertThat(hello.text("/room/match/match/phase")).isEqualTo("setup");

            enter(roomId, vera);

            Frame update = channel.awaitFrame("кадр после входа Веры",
                    frame -> uids(frame.at("/room/snapshot/players")).contains(vera.uid()));
            assertThat(update.type()).isEqualTo("room");
            assertThat(uids(update.at("/room/snapshot/players")))
                    .containsExactlyInAnyOrder(anya.uid(), boris.uid(), vera.uid());

            // Счётчики боезапаса приезжают в том же кадре и по каждому месту:
            // плитке Веры есть что нарисовать сразу, а не после первого выстрела.
            JsonNode veraAmmo = ammoOf(update, vera.uid());
            assertThat(veraAmmo.path("ammo").path("meme").asInt()).as("стартовые мемы: %s", update.raw()).isEqualTo(4);
            assertThat(veraAmmo.path("ammo").path("tomato").asInt()).as("стартовые помидоры").isEqualTo(7);
            assertThat(veraAmmo.path("loadoutCharged").asBoolean()).as("обойма Веры заряжена").isTrue();
        }
    }

    @Test
    @DisplayName("Слово хода приходит только объясняющему, чужой боезапас — счётчиками без мемов, счёт — всем")
    void matchFrameIsAssembledPerListener() throws Exception {
        Player anya = registerArmed("Anya");
        Player boris = registerArmed("Boris");
        Player vera = registerArmed("Vera");
        Player gleb = registerArmed("Gleb");
        List<Player> table = List.of(anya, boris, vera, gleb);

        // ── у Глеба своя обойма: по ней и будет видно, утекает ли чужое ──
        E2EDatabase.seedMemeCatalog(database(), RIVAL_LOADOUT);
        ApiClient.Response rearmed = api.put("/api/v2/profile/me/meme-loadout", gleb.token(),
                Map.of("memeIds", RIVAL_LOADOUT));
        assertThat(rearmed.status()).as(rearmed.raw()).isEqualTo(200);

        String roomId = createRoom(anya);
        for (Player guest : List.of(boris, vera, gleb)) {
            enter(roomId, guest);
        }
        String red = createTeam(roomId, anya, "Красные");
        String blue = createTeam(roomId, anya, "Синие");
        joinTeam(roomId, anya, red);
        joinTeam(roomId, boris, red);
        joinTeam(roomId, vera, blue);
        joinTeam(roomId, gleb, blue);
        for (int i = 0; i < table.size(); i++) {
            ApiClient.Response submitted = api.put("/api/v2/game/" + roomId + "/word-submissions/me",
                    table.get(i).token(), Map.of("words", WORDS.get(i)));
            assertThat(submitted.status()).as(submitted.raw()).isEqualTo(200);
        }

        ApiClient.Response started = api.post("/api/v2/game/" + roomId, anya.token(),
                Map.of("intent", "FIRST_GAME"));
        assertThat(started.status()).as(started.raw()).isEqualTo(201);
        String activeTeam = started.text("/match/currentTeamId");
        String explainerUid = started.at("/match/rosters/" + activeTeam).get(0).asText();
        Player explainer = byUid(table, explainerUid);
        // Слушатель — не объясняющий и не соперник с особой обоймой: его кадр
        // и проверяется на отсутствие чужого.
        Player watcher = table.stream()
                .filter(player -> !player.uid().equals(explainerUid) && !player.uid().equals(gleb.uid()))
                .findFirst()
                .orElseThrow();

        // Каналы открываются после старта, чтобы не вычитывать кадры рассадки
        // и сдачи слов: приветствие уже несёт партию в фазе turnIntro.
        try (Channel explaining = Channel.open(ws("/ws/v2/room/" + roomId, explainer));
             Channel watching = Channel.open(ws("/ws/v2/room/" + roomId, watcher));
             Channel rival = Channel.open(ws("/ws/v2/room/" + roomId, gleb))) {

            Frame explainerHello = explaining.nextFrame("приветствие объясняющего");
            Frame watcherHello = watching.nextFrame("приветствие слушателя");
            Frame rivalHello = rival.nextFrame("приветствие соперника");
            assertThat(explainerHello.type()).isEqualTo("hello");
            assertThat(watcherHello.text("/room/match/match/phase")).isEqualTo("turnIntro");
            assertThat(watcherHello.at("/room/match/match/turn").isNull()).as("до хода слова нет ни у кого").isTrue();

            // Свои слова — шестая подписка — тоже в кадре, и только свои.
            assertThat(watcherHello.number("/room/words/mine")).isEqualTo(5);
            assertThat(watcherHello.number("/room/words/total")).isEqualTo(20);
            assertThat(watcherHello.at("/room/words/words")).hasSize(5);

            // Обойма соперника у него самого — в кадре целиком, у слушателя — ни одного мема.
            assertThat(strings(rivalHello.at("/room/match/arsenal/loadout")))
                    .as("своё содержимое приходит владельцу: %s", rivalHello.raw())
                    .containsExactlyElementsOf(RIVAL_LOADOUT);
            assertThat(strings(watcherHello.at("/room/match/arsenal/loadout")))
                    .as("и слушателю приходит его собственная обойма")
                    .containsExactlyElementsOf(LOADOUT);
            assertNoRivalContent(watcherHello);
            JsonNode rivalAmmo = ammoOf(watcherHello, gleb.uid());
            assertThat(rivalAmmo.path("ammo").path("meme").asInt()).as("счётчик мемов соперника").isEqualTo(4);
            assertThat(rivalAmmo.path("ammo").path("tomato").asInt()).as("счётчик помидоров соперника").isEqualTo(7);
            assertThat(rivalAmmo.path("loadoutCharged").asBoolean()).isTrue();
            assertThat(rivalAmmo.has("loadout")).as("в счётчиках нет обоймы").isFalse();
            assertThat(rivalAmmo.has("available")).isFalse();
            assertThat(rivalAmmo.has("cooldownUntilMs")).as("перезарядка — своё дело").isFalse();

            // ── ход начался: слово — объясняющему, всем остальным — пусто ──
            ApiClient.Response turn = api.post("/api/v2/game/" + roomId + "/turn", explainer.token(), null);
            assertThat(turn.status()).as(turn.raw()).isEqualTo(201);
            String turnId = turn.text("/turn/turnId");
            String firstWord = turn.text("/turn/currentWord");
            assertThat(firstWord).isNotBlank();

            Predicate<Frame> turnRunning = frame -> "active".equals(frame.text("/room/match/match/phase"))
                    && frame.at("/room/match/match/turn").isObject();
            Frame explainerTurn = explaining.awaitFrame("кадр объясняющего с начатым ходом", turnRunning);
            Frame watcherTurn = watching.awaitFrame("кадр слушателя с начатым ходом", turnRunning);

            assertThat(explainerTurn.text("/room/match/match/turn/turnId")).isEqualTo(turnId);
            assertThat(explainerTurn.text("/room/match/match/turn/currentWord"))
                    .as("объясняющий видит слово в кадре").isEqualTo(firstWord);
            assertThat(watcherTurn.text("/room/match/match/turn/turnId")).isEqualTo(turnId);
            assertThat(watcherTurn.at("/room/match/match/turn/currentWord").isNull())
                    .as("остальным слово не приходит: %s", watcherTurn.raw()).isTrue();
            // Свои сданные слова из проверки исключены: слово хода могло быть
            // сдано самим слушателем, и в его же пачке оно лежит по праву.
            assertThat(outsideOwnWords(watcherTurn))
                    .as("слово не протаскивается никаким другим полем кадра").doesNotContain(firstWord);

            // ── угадали: новый счёт доезжает кадром до слушателя ──
            ApiClient.Response guess = api.post("/api/v2/game/" + roomId + "/turn/words/press-0001/guess",
                    explainer.token(), Map.of("turnId", turnId));
            assertThat(guess.status()).as(guess.raw()).isEqualTo(200);
            assertThat(guess.text("/outcome")).isEqualTo("COUNTED");

            Frame scored = watching.awaitFrame("кадр слушателя после угаданного слова",
                    frame -> frame.at("/room/match/match/turn").isObject()
                            && frame.number("/room/match/match/turn/score") == 1);
            assertThat(scored.text("/room/match/match/phase")).isEqualTo("active");
            assertThat(scored.number("/room/match/match/wordsLeft")).isEqualTo(19);
            assertThat(scored.text("/room/match/match/lastGuessedWord"))
                    .as("угаданное слово уже не секрет").isEqualTo(firstWord);
            assertThat(teamScore(scored, activeTeam)).as("счёт команды в кадре").isEqualTo(1);
            assertThat(scored.at("/room/match/match/turn/currentWord").isNull())
                    .as("следующее слово слушателю тоже не приходит").isTrue();
            assertNoRivalContent(scored);
        }
    }

    @Test
    @DisplayName("Съёмку Подмены в кадре видят только снимающий и снимаемый: соседу по столу и зрителю "
            + "она не приезжает, а последней диверсией для них остаётся предыдущий выстрел")
    void replacementRecordingIsFramedForTwo() throws Exception {
        Player anya = registerArmed("Anya");
        Player boris = registerArmed("Boris");
        Player vera = registerArmed("Vera");
        Player gleb = registerArmed("Gleb");
        Player olga = registerArmed("Olga");
        List<Player> table = List.of(anya, boris, vera, gleb);

        // Подмена есть только в режиме диверсий: в обычной партии заказ съёмки
        // отвергается ещё до того, как событию было бы кому утечь.
        String roomId = createRoom(anya, "sabotage");
        for (Player guest : List.of(boris, vera, gleb)) {
            enter(roomId, guest);
        }
        String red = createTeam(roomId, anya, "Красные");
        String blue = createTeam(roomId, anya, "Синие");
        joinTeam(roomId, anya, red);
        joinTeam(roomId, boris, red);
        joinTeam(roomId, vera, blue);
        joinTeam(roomId, gleb, blue);
        for (int i = 0; i < table.size(); i++) {
            ApiClient.Response submitted = api.put("/api/v2/game/" + roomId + "/word-submissions/me",
                    table.get(i).token(), Map.of("words", WORDS.get(i)));
            assertThat(submitted.status()).as(submitted.raw()).isEqualTo(200);
        }
        ApiClient.Response started = api.post("/api/v2/game/" + roomId, anya.token(),
                Map.of("intent", "FIRST_GAME"));
        assertThat(started.status()).as(started.raw()).isEqualTo(201);

        // Четыре роли на одной съёмке: снимаемый объясняет, снимает игрок
        // чужой команды, сосед объясняющего по команде и зритель — третьи лица.
        String activeTeam = started.text("/match/currentTeamId");
        List<String> activeRoster = strings(started.at("/match/rosters/" + activeTeam));
        Player explainer = byUid(table, activeRoster.get(0));
        Player neighbour = byUid(table, activeRoster.get(1));
        Player attacker = table.stream()
                .filter(player -> !activeRoster.contains(player.uid()))
                .findFirst()
                .orElseThrow();

        ApiClient.Response turn = api.post("/api/v2/game/" + roomId + "/turn", explainer.token(), null);
        assertThat(turn.status()).as(turn.raw()).isEqualTo(201);
        String turnId = turn.text("/turn/turnId");
        // Зритель садится после старта: до него в комнату смотреть нельзя.
        ApiClient.Response seated = api.put("/api/v2/room/" + roomId + "/spectators/me", olga.token(), null);
        assertThat(seated.status()).as(seated.raw()).isEqualTo(200);

        // Каналы открываются на уже идущем ходе: приветствие несёт партию в
        // фазе active, и до съёмки читать нечего.
        try (Channel attacking = Channel.open(ws("/ws/v2/room/" + roomId, attacker));
             Channel explaining = Channel.open(ws("/ws/v2/room/" + roomId, explainer));
             Channel neighbouring = Channel.open(ws("/ws/v2/room/" + roomId, neighbour));
             Channel watching = Channel.open(ws("/ws/v2/room/" + roomId, olga))) {

            for (Channel channel : List.of(attacking, explaining, neighbouring)) {
                Frame hello = channel.nextFrame("приветствие игрока");
                assertThat(hello.type()).as(hello.raw()).isEqualTo("hello");
                assertThat(hello.text("/room/match/match/phase")).isEqualTo("active");
            }
            Frame spectatorHello = watching.nextFrame("приветствие зрителя");
            assertThat(spectatorHello.text("/room/snapshot/viewerSeat")).as(spectatorHello.raw()).isEqualTo("spectator");
            // Зритель смотрит идущий ход, но ни слова хода, ни снаряжения у него нет.
            assertThat(spectatorHello.text("/room/match/match/phase")).isEqualTo("active");
            assertThat(spectatorHello.at("/room/match/match/turn/currentWord").isNull())
                    .as("слово хода зрителю: %s", spectatorHello.raw()).isTrue();
            assertThat(spectatorHello.at("/room/match/arsenal").isNull())
                    .as("снаряжение зрителя: %s", spectatorHello.raw()).isTrue();

            // ── публичный выстрел до съёмки: его увидят все, и именно он
            // останется «последней диверсией» у тех, кому съёмку не показывают ──
            ApiClient.Response shot = api.post("/api/v2/game/" + roomId + "/sabotages",
                    attacker.token(), Map.of("type", "tomato"));
            assertThat(shot.status()).as(shot.raw()).isEqualTo(201);
            String tomatoId = shot.text("/event/eventId");
            assertThat(shot.text("/event/type")).isEqualTo("tomato");
            assertThat(tomatoId).isNotBlank();

            // ── заказ съёмки: ответ заказчику несёт и клип, и событие ──
            ApiClient.Response ordered = api.post("/api/v2/game/" + roomId + "/replacement-clips",
                    attacker.token(), Map.of("targetUid", explainer.uid()));
            assertThat(ordered.status()).as(ordered.raw()).isEqualTo(201);
            String clipId = ordered.text("/clip/clipId");
            String eventId = ordered.text("/event/eventId");
            assertThat(ordered.text("/event/type")).isEqualTo("replacement_record");
            assertThat(clipId).isNotBlank();
            assertThat(eventId).isNotBlank();

            Predicate<Frame> recordingSeen = frame -> frame.at("/room/match/match/lastSabotage").isObject()
                    && "replacement_record".equals(frame.text("/room/match/match/lastSabotage/type"));

            // Снимающий: событие, клип и свой срок съёмки; в истории — и выстрел, и съёмка.
            Frame attackerFrame = attacking.awaitFrame("кадр снимающего со съёмкой", recordingSeen);
            assertThat(attackerFrame.text("/room/match/match/lastSabotage/clipId")).isEqualTo(clipId);
            assertThat(attackerFrame.text("/room/match/match/lastSabotage/targetUid")).isEqualTo(explainer.uid());
            assertThat(eventIds(attackerFrame.at("/room/match/match/sabotageEventsRecent")))
                    .containsExactly(tomatoId, eventId);
            assertThat(clipIds(attackerFrame.at("/room/match/clips"))).containsExactly(clipId);
            assertThat(attackerFrame.at("/room/match/match/sabotageLocks/replacementRecordingUntil").asLong())
                    .as("свой срок съёмки виден заказчику").isPositive();

            // Снимаемый: событие — чтобы включить камеру; клип и срок — не его.
            Frame targetFrame = explaining.awaitFrame("кадр снимаемого со съёмкой", recordingSeen);
            assertThat(targetFrame.text("/room/match/match/lastSabotage/clipId")).isEqualTo(clipId);
            assertThat(targetFrame.text("/room/match/match/lastSabotage/attackerUid")).isEqualTo(attacker.uid());
            assertThat(eventIds(targetFrame.at("/room/match/match/sabotageEventsRecent"))).contains(eventId);
            assertThat(targetFrame.at("/room/match/clips")).as("чужой клип снимаемому не отдаётся").isEmpty();
            assertThat(targetFrame.at("/room/match/match/sabotageLocks/replacementRecordingUntil").asLong())
                    .as("чужой срок съёмки снимаемому не отдаётся").isZero();

            // ── третьи лица: кадр после съёмки приходит и им — комната
            // изменилась, — но съёмки в нём нет. Чтобы поймать именно кадр,
            // собранный уже после заказа, следом идёт событие, видимое всем:
            // угаданное слово. Кадры одного сокета идут по порядку, и кадр
            // со счётом 1 собран из состояния, в котором съёмка уже записана.
            ApiClient.Response guess = api.post("/api/v2/game/" + roomId + "/turn/words/press-0001/guess",
                    explainer.token(), Map.of("turnId", turnId));
            assertThat(guess.status()).as(guess.raw()).isEqualTo(200);
            assertThat(guess.text("/outcome")).isEqualTo("COUNTED");

            Predicate<Frame> scored = frame -> frame.at("/room/match/match/turn").isObject()
                    && frame.number("/room/match/match/turn/score") == 1;
            Frame neighbourFrame = neighbouring.awaitFrame("кадр соседа после съёмки и угаданного слова", scored);
            Frame spectatorFrame = watching.awaitFrame("кадр зрителя после съёмки и угаданного слова", scored);
            assertNoRecording(neighbourFrame, "сосед по столу", clipId, eventId, tomatoId);
            assertNoRecording(spectatorFrame, "зритель", clipId, eventId, tomatoId);

            // Адрес HTTP собирает партию тем же сборщиком — и молчит о том же.
            ApiClient.Response neighbourState = api.get("/api/v2/game/" + roomId, neighbour.token());
            assertThat(neighbourState.status()).as(neighbourState.raw()).isEqualTo(200);
            assertThat(neighbourState.raw())
                    .as("GET состояния соседу: %s", neighbourState.raw())
                    .doesNotContain(clipId)
                    .doesNotContain(eventId)
                    .doesNotContain("replacement_record");
            assertThat(neighbourState.text("/match/lastSabotage/eventId")).isEqualTo(tomatoId);
            assertThat(eventIds(neighbourState.at("/match/sabotageEventsRecent"))).containsExactly(tomatoId);
            assertThat(neighbourState.at("/clips")).isEmpty();

            // А заказчику тот же адрес отдаёт всё: сборка одна, различается слушатель.
            ApiClient.Response attackerState = api.get("/api/v2/game/" + roomId, attacker.token());
            assertThat(attackerState.status()).as(attackerState.raw()).isEqualTo(200);
            assertThat(attackerState.text("/match/lastSabotage/clipId")).isEqualTo(clipId);
            assertThat(clipIds(attackerState.at("/clips"))).containsExactly(clipId);
        }
    }

    // ───────────────────────── шаги ─────────────────────────

    private String createRoom(Player host) {
        return createRoom(host, "classic");
    }

    private String createRoom(Player host, String gameMode) {
        ApiClient.Response created = api.post("/api/v2/room", host.token(),
                Map.of("name", "Кадры канала", "capacity", 4, "gameMode", gameMode));
        assertThat(created.status()).as(created.raw()).isEqualTo(201);
        return created.text("/room/roomId");
    }

    private void enter(String roomId, Player guest) {
        ApiClient.Response entered = api.put("/api/v2/room/" + roomId + "/players/me", guest.token(), Map.of());
        assertThat(entered.status()).as("вход %s: %s", guest.nickname(), entered.raw()).isEqualTo(200);
    }

    private String createTeam(String roomId, Player host, String name) {
        ApiClient.Response response = api.post("/api/v2/room/" + roomId + "/teams", host.token(),
                Map.of("name", name));
        assertThat(response.status()).as("команда %s: %s", name, response.raw()).isEqualTo(201);
        return response.text("/team/teamId");
    }

    private void joinTeam(String roomId, Player player, String teamId) {
        ApiClient.Response response = api.put(
                "/api/v2/room/" + roomId + "/teams/" + teamId + "/members/me", player.token(), null);
        assertThat(response.status()).as("посадка %s: %s", player.nickname(), response.raw()).isEqualTo(200);
    }

    // ───────────────────────── чтение кадров ─────────────────────────

    /** Ни один мем соперника не должен встречаться в кадре — где бы то ни было. */
    private static void assertNoRivalContent(Frame frame) {
        for (String memeId : RIVAL_LOADOUT) {
            assertThat(frame.raw()).as("чужой мем %s в кадре слушателя", memeId).doesNotContain(memeId);
        }
    }

    /**
     * В кадре третьего лица от съёмки не должно остаться ни следа: ни события,
     * ни номера клипа, ни самого слова {@code replacement_record} — где бы то
     * ни было, а не только в поле, где его ждут. При этом последней диверсией
     * для него остаётся предыдущий публичный выстрел, а не пусто: иначе поле
     * прыгало бы с каждой чужой съёмкой, и сцена не отличила бы «диверсий не
     * было» от «была, но не про тебя».
     */
    private static void assertNoRecording(Frame frame, String listener, String clipId, String eventId,
                                          String visibleEventId) {
        assertThat(frame.raw())
                .as("%s видит съёмку Подмены: %s", listener, frame.raw())
                .doesNotContain(clipId)
                .doesNotContain(eventId)
                .doesNotContain("replacement_record");
        assertThat(frame.text("/room/match/match/lastSabotage/eventId"))
                .as("%s: последняя диверсия — предыдущий выстрел, а не съёмка", listener).isEqualTo(visibleEventId);
        assertThat(frame.text("/room/match/match/lastSabotage/type")).isEqualTo("tomato");
        assertThat(eventIds(frame.at("/room/match/match/sabotageEventsRecent")))
                .as("%s: в истории один выстрел и никакой съёмки", listener).containsExactly(visibleEventId);
        assertThat(frame.at("/room/match/clips")).as("%s: чужих клипов нет", listener).isEmpty();
        assertThat(frame.at("/room/match/match/sabotageLocks/replacementRecordingUntil").asLong())
                .as("%s: чужой срок съёмки не отдаётся", listener).isZero();
    }

    private static List<String> eventIds(JsonNode events) {
        List<String> ids = new ArrayList<>();
        events.forEach(event -> ids.add(event.path("eventId").asText()));
        return ids;
    }

    private static List<String> clipIds(JsonNode clips) {
        List<String> ids = new ArrayList<>();
        clips.forEach(clip -> ids.add(clip.path("clipId").asText()));
        return ids;
    }

    /** Кадр без своей пачки слов: всё, где слову хода появляться нельзя. */
    private static String outsideOwnWords(Frame frame) {
        return frame.at("/room/snapshot").toString()
                + frame.at("/room/chat").toString()
                + frame.at("/room/match").toString();
    }

    private static JsonNode ammoOf(Frame frame, String uid) {
        for (JsonNode item : frame.at("/room/match/ammo")) {
            if (uid.equals(item.path("uid").asText())) {
                return item;
            }
        }
        throw new AssertionError("в кадре нет счётчиков игрока " + uid + ": " + frame.raw());
    }

    private static int teamScore(Frame frame, String teamId) {
        for (JsonNode team : frame.at("/room/match/match/teams")) {
            if (teamId.equals(team.path("teamId").asText())) {
                return team.path("score").asInt();
            }
        }
        throw new AssertionError("в кадре нет команды " + teamId + ": " + frame.raw());
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

    private static Player byUid(List<Player> table, String uid) {
        return table.stream()
                .filter(player -> player.uid().equals(uid))
                .findFirst()
                .orElseThrow(() -> new AssertionError("за столом нет игрока " + uid));
    }
}
