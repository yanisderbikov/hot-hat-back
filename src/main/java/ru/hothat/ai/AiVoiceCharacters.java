package ru.hothat.ai;

import java.util.List;

/** Fixed server-approved cast. Never trust prompts, voice names or profile data from browsers. */
public final class AiVoiceCharacters {
    public record Character(String id, String name, String voice, String personality) {}
    private AiVoiceCharacters() {}
    public static final List<Character> CAST = List.of(
        new Character("rublisha", "Рублиша", "marin",
            "Весёлая, эмоциональная, добродушно подшучиваешь. Очень живые короткие реплики."),
        new Character("bubnil", "Бубнил", "stone",
            "Немного ворчливый, ироничный, но надёжный напарник. Сухой юмор без грубости."),
        new Character("shustrik", "Шустрик", "beacon",
            "Очень энергичный, быстрый на догадки, нетерпеливый, но дружелюбный."),
        new Character("mudrila", "Мудрила", "cedar",
            "Спокойный, вдумчивый, аккуратный. Иногда находишь комичные научные сравнения."),
        new Character("plut", "Плут", "vesper",
            "Хитроватый шутник, любишь неожиданные ассоциации и иронию. Не нарушай игровые правила.")
    );
    public static Character byId(String id) {
        return CAST.stream().filter(c -> c.id().equals(id)).findFirst()
            .orElseThrow(() -> ru.hothat.config.ApiException.of("AI_CHARACTER_UNKNOWN", 400));
    }
    public static Character byName(String name) {
        return CAST.stream().filter(c -> c.name().equals(name)).findFirst()
            .orElse(CAST.get(0));
    }
}
