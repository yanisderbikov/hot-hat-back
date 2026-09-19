package ru.hothat.e2e;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import ru.hothat.e2e.support.ApiClient;
import ru.hothat.e2e.support.E2ETest;
import ru.hothat.e2e.support.Player;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Путь от регистрации до первого закрытого хода — тот самый, который до сих
 * пор проходили руками через curl.
 *
 * <p>Один тест на весь путь, а не десяток мелких: проверяется не каждый адрес
 * по отдельности (для этого есть модульные тесты правил), а то, что двенадцать
 * адресов складываются в партию. Разбить его значило бы двенадцать раз заново
 * доводить мир до нужного состояния и потерять ровно то, что здесь ценно, —
 * порядок.
 */
class MatchScenarioE2ETest extends E2ETest {

    /** Слова у каждого свои: шляпа отбрасывает повторы, а нам нужно двадцать. */
    private static final List<List<String>> WORDS = List.of(
            List.of("Абажур", "Водопад", "Кофемолка", "Полынья", "Скрипка"),
            List.of("Батискаф", "Гербарий", "Дюна", "Ересь", "Жёлудь"),
            List.of("Заноза", "Изразец", "Кальян", "Лебёдка", "Мельница"),
            List.of("Наковальня", "Оазис", "Пряжка", "Ржавчина", "Сургуч"));

    @Test
    @DisplayName("Четверо доходят от регистрации до закрытого хода, и повторное нажатие не даёт второго очка")
    void fourPlayersPlayOneTurn() {
        Player anya = registerArmed("Anya");
        Player boris = registerArmed("Boris");
        Player vera = registerArmed("Vera");
        Player gleb = registerArmed("Gleb");
        List<Player> table = List.of(anya, boris, vera, gleb);

        // ── комната: хозяин уже за столом, остальные входят своими местами ──
        ApiClient.Response created = api.post("/api/v2/room", anya.token(),
                Map.of("name", "Сквозной прогон", "capacity", 4));
        assertThat(created.status()).as(created.raw()).isEqualTo(201);
        String roomId = created.text("/room/roomId");

        for (Player guest : List.of(boris, vera, gleb)) {
            ApiClient.Response entered = api.put("/api/v2/room/" + roomId + "/players/me", guest.token(), Map.of());
            assertThat(entered.status()).as("вход %s: %s", guest.nickname(), entered.raw()).isEqualTo(200);
        }

        // ── две команды по паре: партия требует ровно двоих в каждой ──
        String red = createTeam(roomId, anya, "Красные");
        String blue = createTeam(roomId, anya, "Синие");
        joinTeam(roomId, anya, red);
        joinTeam(roomId, boris, red);
        joinTeam(roomId, vera, blue);
        joinTeam(roomId, gleb, blue);

        // ── слова: по пять с каждого, и счётчик шляпы растёт на пять за раз ──
        for (int i = 0; i < table.size(); i++) {
            Player player = table.get(i);
            ApiClient.Response submitted = api.put("/api/v2/game/" + roomId + "/word-submissions/me",
                    player.token(), Map.of("words", WORDS.get(i)));
            assertThat(submitted.status()).as("слова %s: %s", player.nickname(), submitted.raw()).isEqualTo(200);
            assertThat(submitted.number("/mine")).isEqualTo(5);
            assertThat(submitted.number("/total")).isEqualTo(5 * (i + 1));
        }

        // ── старт партии ──
        ApiClient.Response started = api.post("/api/v2/game/" + roomId, anya.token(),
                Map.of("intent", "FIRST_GAME"));
        assertThat(started.status()).as(started.raw()).isEqualTo(201);
        assertThat(started.number("/teamCount")).isEqualTo(2);
        assertThat(started.number("/playerCount")).isEqualTo(4);
        assertThat(started.number("/wordCount")).isEqualTo(20);

        String activeTeam = started.text("/match/currentTeamId");
        String explainerUid = started.at("/match/rosters/" + activeTeam).get(0).asText();
        Player explainer = table.stream()
                .filter(player -> player.uid().equals(explainerUid))
                .findFirst()
                .orElseThrow(() -> new AssertionError("объясняющий не из-за стола: " + started.raw()));

        // ── ход: слово тянет сервер, оно видно только объясняющему ──
        ApiClient.Response turn = api.post("/api/v2/game/" + roomId + "/turn", explainer.token(), null);
        assertThat(turn.status()).as(turn.raw()).isEqualTo(201);
        assertThat(turn.text("/outcome")).isEqualTo("STARTED");
        String turnId = turn.text("/turn/turnId");
        String firstWord = turn.text("/turn/currentWord");
        assertThat(firstWord).isNotBlank();
        assertThat(turn.number("/turn/wordsLeft")).isEqualTo(20);

        // ── ключ нажатия проверяется как идентификатор, а не как слово ──
        // Здесь, посреди хода, а не отдельным тестом: до старта партии тот же
        // адрес отвечает 403 «ход не ваш», и проверка формата до него не дошла бы.
        ApiClient.Response tooShort = guess(roomId, explainer, "press", turnId);
        assertThat(tooShort.status()).as("ключ короче шести знаков: %s", tooShort.raw()).isEqualTo(400);

        // ── угадали ──
        // Ключ в адресе — идентификатор нажатия, а не слово: его придумывает
        // клиент, и по нему сервер узнаёт повтор.
        String press = "press-0001";
        ApiClient.Response guess = guess(roomId, explainer, press, turnId);
        assertThat(guess.status()).as(guess.raw()).isEqualTo(200);
        assertThat(guess.text("/outcome")).isEqualTo("COUNTED");
        assertThat(guess.text("/word")).isEqualTo(firstWord);
        assertThat(guess.number("/turnScore")).isEqualTo(1);
        assertThat(guess.number("/wordsLeft")).isEqualTo(19);
        assertThat(guess.at("/nextWord").asText()).isNotBlank();
        assertThat(teamScore(roomId, explainer, activeTeam)).isEqualTo(1);

        // ── то же нажатие второй раз: повтор по разорванной связи ──
        ApiClient.Response again = guess(roomId, explainer, press, turnId);
        assertThat(again.status()).as(again.raw()).isEqualTo(200);
        assertThat(again.text("/outcome")).isEqualTo("REPEATED");
        assertThat(again.text("/word")).isEqualTo(firstWord);
        assertThat(again.number("/turnScore")).as("повтор не даёт второго очка").isEqualTo(1);
        assertThat(again.number("/wordsLeft")).as("повтор не крутит шляпу").isEqualTo(19);
        assertThat(teamScore(roomId, explainer, activeTeam))
                .as("счёт команды после повтора").isEqualTo(1);

        // ── пропустили: слово возвращается в шляпу, очков не приносит ──
        String secondWord = guess.text("/nextWord");
        ApiClient.Response skip = api.post(
                "/api/v2/game/" + roomId + "/turn/words/press-0002/skip", explainer.token(),
                Map.of("turnId", turnId));
        assertThat(skip.status()).as(skip.raw()).isEqualTo(200);
        assertThat(skip.text("/outcome")).isEqualTo("SKIPPED");
        assertThat(skip.text("/word")).isEqualTo(secondWord);
        assertThat(skip.number("/turnScore")).as("пропуск не меняет счёт").isEqualTo(1);
        assertThat(skip.number("/wordsLeft")).as("пропущенное вернулось в шляпу").isEqualTo(19);

        // ── завершили ход ──
        ApiClient.Response completed = api.post("/api/v2/game/" + roomId + "/turn/completion",
                explainer.token(), Map.of("turnId", turnId));
        assertThat(completed.status()).as(completed.raw()).isEqualTo(200);
        assertThat(completed.text("/outcome")).isEqualTo("CLOSED");
        assertThat(completed.number("/preliminaryScore")).isEqualTo(1);
        assertThat(completed.text("/match/phase")).isEqualTo("appeal");

        // ── апелляция называет свой ход ──
        // Без этого фаза апелляции была тупиком: закрытый ход уже стёрт из
        // /match/turn, а «подвести итог» и «передать ход» требуют turnId. Тот,
        // кто ход закрывал, держал его в памяти вкладки — но перезагрузка
        // страницы, переподключение или подведение итога другим участником
        // назвать ход уже не могли, и партия оставалась в appeal навсегда.
        // Поэтому спрашиваем не тем, кто закрывал, а соседом по столу и
        // отдельным чтением состояния: так проверяется именно то, что
        // идентификатор доехал в снимке, а не остался в памяти клиента.
        Player bystander = table.stream()
                .filter(player -> !player.uid().equals(explainerUid))
                .findFirst()
                .orElseThrow();
        ApiClient.Response seen = api.get("/api/v2/game/" + roomId, bystander.token());
        assertThat(seen.status()).as(seen.raw()).isEqualTo(200);
        assertThat(seen.text("/match/phase")).isEqualTo("appeal");
        assertThat(seen.at("/match/turn").isNull()).as("закрытый ход из снимка убран").isTrue();
        assertThat(seen.text("/match/appeal/turnId"))
                .as("апелляция называет разбираемый ход: %s", seen.raw())
                .isEqualTo(turnId);
    }

    @Test
    @DisplayName("Обойма учётки не принимает несуществующий мем")
    void defaultLoadoutRejectsUnknownMeme() {
        // Проверок было две — форма идентификатора и наличие ролика в
        // каталоге, — и обе стояли только у обоймы ПАРТИИ. Обойма УЧЁТКИ
        // принимала любые пять строк и отвечала ready: true, а дальше её
        // спрашивают только на размер: пять выдуманных мемов открывали
        // создание комнаты, вход в неё, место зрителя, билет подбора и
        // преполёт пары.
        Player player = register("Loadout");

        ApiClient.Response malformed = api.put("/api/v2/profile/me/meme-loadout", player.token(),
                Map.of("memeIds", List.of("ghost-1")));
        assertThat(malformed.status()).as("форма идентификатора: %s", malformed.raw()).isEqualTo(400);

        ApiClient.Response missing = api.put("/api/v2/profile/me/meme-loadout", player.token(),
                Map.of("memeIds", List.of("builtin-e2e-never-published")));
        assertThat(missing.status()).as("мема нет в каталоге: %s", missing.raw()).isEqualTo(404);
        assertThat(missing.raw()).contains("MEME_NOT_FOUND");

        // Обойма из настоящих роликов по-прежнему сохраняется, и черновик
        // короче пяти — тоже: неполнота не ошибка, несуществующий мем — ошибка.
        ApiClient.Response draft = api.put("/api/v2/profile/me/meme-loadout", player.token(),
                Map.of("memeIds", LOADOUT.subList(0, 2)));
        assertThat(draft.status()).as(draft.raw()).isEqualTo(200);
        assertThat(draft.at("/status/ready").asBoolean()).as("черновик не считается готовым").isFalse();

        ApiClient.Response full = api.put("/api/v2/profile/me/meme-loadout", player.token(),
                Map.of("memeIds", LOADOUT));
        assertThat(full.status()).as(full.raw()).isEqualTo(200);
        assertThat(full.at("/status/ready").asBoolean()).isTrue();
    }

    @Test
    @DisplayName("Комната не создаётся, пока не заряжены пять мемов")
    void roomNeedsFullLoadout() {
        // Обойма — единственная преграда на входе в комнату, и именно на ней
        // спотыкался ручной прогон: без неё дальше первого шага не пройти.
        Player bare = register("Naked");

        ApiClient.Response refused = api.post("/api/v2/room", bare.token(), Map.of("name", "Без обоймы"));

        assertThat(refused.status()).isEqualTo(409);
        assertThat(refused.raw()).contains("DEFAULT_LOADOUT_REQUIRED");
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

    private ApiClient.Response guess(String roomId, Player explainer, String pressId, String turnId) {
        return api.post("/api/v2/game/" + roomId + "/turn/words/" + pressId + "/guess",
                explainer.token(), Map.of("turnId", turnId));
    }

    /** Счёт команды глазами партии: очко должно доехать до строки команды. */
    private int teamScore(String roomId, Player viewer, String teamId) {
        ApiClient.Response state = api.get("/api/v2/game/" + roomId, viewer.token());
        assertThat(state.status()).as(state.raw()).isEqualTo(200);
        for (JsonNode team : state.at("/match/teams")) {
            if (teamId.equals(team.get("teamId").asText())) {
                return team.get("score").asInt();
            }
        }
        throw new AssertionError("команды " + teamId + " нет в партии: " + state.raw());
    }
}
