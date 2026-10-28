package ru.hothat.codeteam.usecase;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CodeTeamPilotTruncationTest {

    @Test
    void marksOutputLimitAsIncomplete() {
        String answer = RunCodeTeamPilotUseCase.markTruncatedResponse("Игроки использовали конкре", "length");
        assertTrue(answer.startsWith("Игроки использовали конкре"));
        assertTrue(answer.contains("Отчёт обрезан"));
    }

    @Test
    void preservesCompleteReport() {
        assertEquals("Полный отчёт.", RunCodeTeamPilotUseCase.markTruncatedResponse("Полный отчёт.", "stop"));
    }

    @Test
    void understandsAlternativeTokenLimitReason() {
        assertTrue(RunCodeTeamPilotUseCase.markTruncatedResponse("Начало", "MAX_TOKENS").contains("неполный"));
    }
}
