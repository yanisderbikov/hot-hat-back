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
    @Test
    void acceptsEverydayTextWithoutQuotationMarksAndKeepsNaturalPause() {
        NeuroScenePlan plan = NeuroScenePlan.plan(
                "Этот игрок должен сказать - а вы все еще кипятите? пауза тогда мы идем к вам "
                + "и показывает большой палец смотря в камеру.");
        assertEquals("а вы все еще кипятите? ... тогда мы идем к вам", plan.spokenText());
        assertTrue(plan.durationSeconds() <= 10);
        assertTrue(plan.videoPrompt().contains("BACKGROUND LOCK"));
        assertTrue(plan.videoPrompt().contains("показывает большой палец"));
    }

    @Test
    void unquotedDialogueMayContainOrdinaryConjunctions() {
        var plan = NeuroScenePlan.plan("Смотрит в камеру и говорит Привет всем друзьям и хорошего дня!");
        assertEquals("Привет всем друзьям и хорошего дня!", plan.spokenText());
    }

    @Test
    void preservesOriginalRoomEvenWhenPlayerStandsUpAndExits() {
        var plan = NeuroScenePlan.plan("Говорит Привет! потом встаёт и уходит из кадра.");
        assertTrue(plan.videoPrompt().contains("BACKGROUND LOCK"));
        assertTrue(plan.videoPrompt().contains("Do not move the camera"));
    }

    @Test
    void explicitRequestCanChangeBackground() {
        var plan = NeuroScenePlan.plan("Говорит Привет! Смени фон на космос.");
        assertFalse(plan.videoPrompt().contains("BACKGROUND LOCK"));
    }

    @Test
    void vagueFreeTextIsRejectedBeforeAnyPaidCall() {
        var error = assertThrows(ResponseStatusException.class,
                () -> NeuroScenePlan.plan("Пусть он делает что-то смешное, а потом все смеются."));
        assertTrue(error.getReason().contains("NOT_UNAMBIGUOUS"));
    }

}
