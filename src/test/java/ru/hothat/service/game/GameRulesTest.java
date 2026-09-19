package ru.hothat.service.game;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import ru.hothat.game.domain.MatchPhase;
import ru.hothat.game.domain.WeaponRegistry;
import ru.hothat.model.room.Room;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Боезапас, обойма мемов и служебные расчёты партии. */
class GameRulesTest {

    @Nested
    @DisplayName("Боезапас")
    class Arsenal {

        @Test
        @DisplayName("У игрока без сохранённого боезапаса он равен стартовому")
        void missingArsenalIsTheBaseOne() {
            assertThat(GameRules.arsenal(null)).isEqualTo(GameRules.BASE_ARSENAL);
        }

        @Test
        @DisplayName("Недостающие ключи добираются из стартового набора, лишние отбрасываются")
        void unknownKeysAreDroppedAndMissingOnesFilled() {
            Map<String, Object> stored = new LinkedHashMap<>();
            stored.put("tomato", 1);
            stored.put("огурец", 99);

            Map<String, Integer> arsenal = GameRules.arsenal(stored);

            assertThat(arsenal).containsOnlyKeys(GameRules.ARSENAL_KEYS.toArray(String[]::new));
            assertThat(arsenal).containsEntry("tomato", 1);
            assertThat(arsenal).containsEntry("meme", GameRules.BASE_ARSENAL.get("meme"));
        }

        @Test
        @DisplayName("Отрицательный заряд читается как ноль, а не как долг")
        void negativeAmmoBecomesZero() {
            assertThat(GameRules.arsenal(Map.of("tomato", -5))).containsEntry("tomato", 0);
        }

        @Test
        @DisplayName("Заряд, сохранённый строкой, читается числом: так лежат старые документы")
        void numericStringIsReadAsNumber() {
            assertThat(GameRules.arsenal(Map.of("tomato", "3"))).containsEntry("tomato", 3);
        }

        @Test
        @DisplayName("Каталог оружия и стартовый боезапас партии описывают один и тот же набор")
        void catalogAndBaseArsenalAgree() {
            assertThat(WeaponRegistry.baseArsenal()).isEqualTo(GameRules.BASE_ARSENAL);
            assertThat(WeaponRegistry.ammoKeys())
                    .containsExactlyInAnyOrderElementsOf(GameRules.ARSENAL_KEYS);
        }
    }

    @Nested
    @DisplayName("Обойма мемов")
    class Loadout {

        @Test
        @DisplayName("Обойма чистится от пустот и повторов")
        void loadoutDropsBlanksAndDuplicates() {
            List<String> loadout = GameRules.normalizedLoadout(
                    new ArrayList<>(List.of("meme-1", "meme-1", "  ", "meme-2")));

            assertThat(loadout).containsExactly("meme-1", "meme-2");
        }

        @Test
        @DisplayName("В обойму влезает ровно пять мемов, лишние отбрасываются")
        void loadoutIsCappedAtFive() {
            List<String> loadout = GameRules.normalizedLoadout(
                    List.of("m1", "m2", "m3", "m4", "m5", "m6", "m7"));

            assertThat(loadout).hasSize(GameRules.MEME_LOADOUT_SIZE);
            assertThat(loadout).containsExactly("m1", "m2", "m3", "m4", "m5");
        }

        @Test
        @DisplayName("Пустая обойма не роняет расчёт")
        void nullLoadoutIsEmpty() {
            assertThat(GameRules.normalizedLoadout(null)).isEmpty();
        }
    }

    @Nested
    @DisplayName("Очередь выдачи мемов")
    class MemeQueue {

        private GameRules.MemeQueue queue(List<String> available, List<String> reserve, List<String> recycle) {
            GameRules.MemeQueue queue = new GameRules.MemeQueue();
            queue.loadout = new ArrayList<>(List.of("m1", "m2", "m3", "m4", "m5"));
            queue.available = new ArrayList<>(available);
            queue.reserve = new ArrayList<>(reserve);
            queue.recycle = new ArrayList<>(recycle);
            return queue;
        }

        @Test
        @DisplayName("Награда берётся сначала из резерва")
        void reserveGoesFirst() {
            GameRules.MemeQueue granted = GameRules.grantMemes(
                    queue(List.of("m1"), List.of("m4", "m5"), List.of("m2")), 1);

            assertThat(granted.available).containsExactly("m1", "m4");
            assertThat(granted.reserve).containsExactly("m5");
            assertThat(granted.recycle).containsExactly("m2");
        }

        @Test
        @DisplayName("Когда резерв кончился, в ход идёт переработка")
        void recycleGoesAfterReserve() {
            GameRules.MemeQueue granted = GameRules.grantMemes(
                    queue(List.of(), List.of(), List.of("m2", "m3")), 1);

            assertThat(granted.available).containsExactly("m2");
            assertThat(granted.recycle).containsExactly("m3");
        }

        @Test
        @DisplayName("Когда кончилось всё, обойма идёт по кругу и курсор двигается")
        void emptyQueueCyclesThroughLoadout() {
            GameRules.MemeQueue granted = GameRules.grantMemes(
                    queue(List.of(), List.of(), List.of()), 2);

            assertThat(granted.available).containsExactly("m1", "m2");
            assertThat(granted.cycleCursor).isEqualTo(2);
        }

        @Test
        @DisplayName("Пустая обойма не выдаёт ничего и не зацикливается")
        void emptyLoadoutGrantsNothing() {
            GameRules.MemeQueue empty = new GameRules.MemeQueue();

            assertThat(GameRules.grantMemes(empty, 3).available).isEmpty();
        }

        @Test
        @DisplayName("Партия, начатая до появления очереди, восстанавливается из использованных и заряда")
        void legacyMatchIsMigrated() {
            Map<String, Object> runtime = new LinkedHashMap<>();
            runtime.put("memeLoadout", List.of("m1", "m2", "m3", "m4", "m5"));
            runtime.put("usedMemeIds", List.of("m1"));
            runtime.put("arsenal", Map.of("meme", 2));

            GameRules.MemeQueue restored = GameRules.memeQueue(runtime);

            assertThat(restored.recycle).containsExactly("m1");
            assertThat(restored.available).containsExactly("m2", "m3");
            assertThat(restored.reserve).containsExactly("m4", "m5");
        }
    }

    @Nested
    @DisplayName("Служебные расчёты комнаты")
    class RoomMath {

        @Test
        @DisplayName("Дедлайн хода считается от его начала и длительности")
        void deadlineIsStartPlusDuration() {
            Room room = new Room();
            room.setTurnStartedAt(Instant.ofEpochMilli(1_000_000));
            room.setTurnDurationSeconds(12.5);

            assertThat(GameRules.currentTurnDeadline(room)).isEqualTo(1_012_500);
        }

        @Test
        @DisplayName("У комнаты без начала хода читается дедлайн прошлой версии")
        void legacyDeadlineIsUsed() {
            Room room = new Room();
            room.setTurnStartedAt(null);
            room.setTurnEndsAt(1_234_567L);

            assertThat(GameRules.currentTurnDeadline(room)).isEqualTo(1_234_567L);
        }

        @Test
        @DisplayName("Состав партии собирается по командам без повторов")
        void gamePlayersComeFromTeamRosters() {
            Room room = new Room();
            Map<String, Object> rosters = new LinkedHashMap<>();
            rosters.put("team-red", List.of("uid-ann", "uid-bob"));
            rosters.put("team-blue", List.of("uid-cat", "uid-ann"));
            room.setTeamRosters(rosters);

            assertThat(GameRules.allGamePlayers(room)).containsExactly("uid-ann", "uid-bob", "uid-cat");
            assertThat(GameRules.rosterForTeam(room, "team-red")).containsExactly("uid-ann", "uid-bob");
        }

        @Test
        @DisplayName("В историю диверсий не попадают события прошлых партий")
        void sabotageHistoryDropsPreviousMatches() {
            Room room = new Room();
            room.setGameNumber(2);
            List<Object> stored = new ArrayList<>();
            stored.add(Map.of("id", "старая-1", "gameNumber", 1));
            stored.add(Map.of("id", "текущая-1", "gameNumber", 2));
            stored.add(Map.of("id", "старая-2", "gameNumber", 1));
            room.setSabotageEventsRecent(stored);

            List<Object> recent = GameRules.appendRecentSabotage(room, Map.of("id", "новая", "gameNumber", 2));

            List<Object> ids = new ArrayList<>();
            recent.forEach(event -> ids.add(((Map<?, ?>) event).get("id")));

            assertThat(recent).hasSize(2);
            assertThat(ids).containsExactly("текущая-1", "новая");
        }

        @Test
        @DisplayName("История диверсий хранит последние двадцать четыре события")
        void sabotageHistoryKeepsLastTwentyFour() {
            Room room = new Room();
            room.setGameNumber(2);
            List<Object> stored = new ArrayList<>();
            for (int i = 0; i < 30; i++) {
                stored.add(Map.of("id", "e" + i, "gameNumber", 2));
            }
            room.setSabotageEventsRecent(stored);

            List<Object> recent = GameRules.appendRecentSabotage(room, Map.of("id", "новая", "gameNumber", 2));

            assertThat(recent).hasSize(24);
            assertThat(recent).first().isEqualTo(Map.of("id", "e7", "gameNumber", 2));
            assertThat(recent).last().isEqualTo(Map.of("id", "новая", "gameNumber", 2));
        }

        @Test
        @DisplayName("Ставить на паузу можно ровно те же фазы, что знает движок партии")
        void pausablePhasesAgreeWithTheEngine() {
            assertThat(MatchPhase.PAUSABLE)
                    .extracting(MatchPhase::code)
                    .containsExactlyInAnyOrderElementsOf(GameRules.PAUSABLE_PHASES);
        }
    }
}
