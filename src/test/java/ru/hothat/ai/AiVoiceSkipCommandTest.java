package ru.hothat.ai;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AiVoiceSkipCommandTest {
    @Test void acceptsNaturalNextWordCommands() {
        for(String speech:new String[]{
            "Давай след слово!", "след слово", "Следующее слово",
            "Давай следующее", "Давай другое", "Пропусти",
            "Перейдем к следующему слову"
        }) assertTrue(AiVoiceGameService.explicitSkipRequested(speech),speech);
    }
    @Test void doesNotSkipOnUnrelatedHints() {
        for(String speech:new String[]{
            "Следуй за мной", "Думаю, это самолёт", "след за кораблём",
            "какой цвет у слова", "давай скорее думай"
        }) assertFalse(AiVoiceGameService.explicitSkipRequested(speech),speech);
    }
}
