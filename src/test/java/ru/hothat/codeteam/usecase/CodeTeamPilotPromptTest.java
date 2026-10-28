package ru.hothat.codeteam.usecase;

import org.junit.jupiter.api.Test;
import ru.hothat.codeteam.api.dto.CodeTeamPilotRequestDTO;
import ru.hothat.config.ApiException;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CodeTeamPilotPromptTest {

    @Test
    void excludesSyntheticAndBotEvidence() {
        var prepared = CodeTeamPilotPrompt.prepare(sample(true, true));
        assertEquals(2, prepared.evidenceCount());
        assertTrue(prepared.prompt().contains("[one]"));
        assertTrue(prepared.prompt().contains("[two]"));
        assertFalse(prepared.prompt().contains("[bot]"));
        assertFalse(prepared.prompt().contains("[impostor]"));
    }

    @Test
    void requiresConsentFromBothPeople() {
        assertThrows(ApiException.class, () -> CodeTeamPilotPrompt.prepare(sample(false, true)));
    }

    @Test
    void requiresTwoDifferentPeople() {
        var base = sample(true, true);
        var duplicate = new CodeTeamPilotRequestDTO(base.teamClass(),
                List.of(base.players().get(0), base.players().get(0)), base.evidence(), false);
        assertThrows(ApiException.class, () -> CodeTeamPilotPrompt.prepare(duplicate));
    }

    @Test
    void rejectsEvidenceFromUnrecognizedPerson() {
        var base = sample(true, true);
        var forged = new CodeTeamPilotRequestDTO(base.teamClass(), base.players(), List.of(
                new CodeTeamPilotRequestDTO.Evidence("one", "unknown", "guess", "угадал", "human", false),
                base.evidence().get(1)), false);
        assertThrows(ApiException.class, () -> CodeTeamPilotPrompt.prepare(forged));
    }

    private CodeTeamPilotRequestDTO sample(boolean aConsent, boolean bConsent) {
        return new CodeTeamPilotRequestDTO("friends", List.of(
                new CodeTeamPilotRequestDTO.Player("p1", "Игрок один", aConsent),
                new CodeTeamPilotRequestDTO.Player("p2", "Игрок два", bConsent)), List.of(
                new CodeTeamPilotRequestDTO.Evidence("one", "p1", "hint", "объяснил коротко", "human", false),
                new CodeTeamPilotRequestDTO.Evidence("two", "p2", "guess", "угадал слово", "human", false),
                new CodeTeamPilotRequestDTO.Evidence("bot", "p1", "speech", "фальшивый текст", "bot", false),
                new CodeTeamPilotRequestDTO.Evidence("impostor", "p2", "speech", "подмена", "human", true)), false);
    }
}
