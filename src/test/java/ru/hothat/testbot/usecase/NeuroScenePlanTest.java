package ru.hothat.testbot.usecase;

import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.*;

class NeuroScenePlanTest {
    @Test
    void plansDifferentHumanWrittenActionsAndSpeechWithinTenSeconds() {
        NeuroScenePlan plan = NeuroScenePlan.plan(
                "Игрок поворачивается к вебке и говорит «А вы всё ещё кипятите?» " +
                "делает паузу и говорит «Тогда мы идём к вам!», показывает большой палец, " +
                "грозит кулаком, встаёт и уходит.");
        assertTrue(plan.durationSeconds() <= 10);
        assertTrue(plan.durationSeconds() >= 4);
        assertEquals("А вы всё ещё кипятите? ... Тогда мы идём к вам!",plan.spokenText());
        assertTrue(plan.videoPrompt().contains("грозит кулаком"));
        assertTrue(plan.videoPrompt().contains("встаёт и уходит"));
        assertTrue(plan.videoPrompt().contains("ONLY spoken dialogue"));
        assertTrue(plan.videoPrompt().length() <= 2500);
    }

    @Test
    void shorterActionSelectsLessThanMaximumDuration() {
        NeuroScenePlan plan = NeuroScenePlan.plan("Посмотреть в камеру и сказать «Привет!»");
        assertTrue(plan.durationSeconds() < 10);
        assertEquals("Привет!",plan.spokenText());
    }

    @Test
    void refusesOverlengthSceneBeforeAnyPaidRequests() {
        String line = "Этот игрок должен сказать «" + "Большое длинное предложение о новой " .repeat(20)
                + "сцене» и встать, выйти и помахать на прощание.";
        assertThrows(ResponseStatusException.class, () -> NeuroScenePlan.plan(line));
    }

    @Test
    void refusesAmbiguousUnquotedSpokenWords() {
        var e=assertThrows(ResponseStatusException.class,
                () -> NeuroScenePlan.plan("Игрок должен сказать привет всем и затем улыбнуться"));
        assertTrue(e.getReason().contains("QUOTATION"));
    }

    @Test
    void neverSilentlyCutsSpeechToFitPriceCap() {
        String assignment = "Игрок с выражением говорит «" + "а вы все еще кипятите и мы идем к вам, "
                .repeat(8) + "» и машет рукой на прощание";
        assertThrows(ResponseStatusException.class, () -> NeuroScenePlan.plan(assignment));
    }
}
