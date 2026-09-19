package ru.hothat.lobby.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import ru.hothat.profile.api.dto.DivisionLanguage;
import ru.hothat.team.api.dto.GameMode;

/**
 * Заявка на быструю игру.
 *
 * <p>Язык партии необязателен: не назвали — играем на языке своего дивизиона.
 * Назвать можно свой дивизион либо общий английский, всё остальное движок
 * отвергает кодом {@code QUICK_LANGUAGE_INVALID} — правило принадлежит
 * подбору, а не форме запроса, и повторять его здесь значило бы завести
 * второе место, которое разойдётся с первым.
 *
 * <p>Оба закрытых набора взяты у соседей, а не заведены свои: девять кодов
 * языка уже перечислены в области профиля, два режима — в области команды,
 * и вторая копия того же списка есть ровно та беда, которую переезд затевался
 * чинить. Своё перечисление языков здесь было бы четвёртой копией девяти
 * кодов в проекте, а своё перечисление режимов — второй схемой с именем
 * {@code GameMode} в одной спецификации.
 */
@Schema(description = "Запрос на подбор быстрой игры")
public record OpenCasualTicketRequestDTO(

        @Schema(description = "Режим партии", example = "classic")
        @NotNull(message = "Нужен режим партии.")
        GameMode gameMode,

        @Schema(description = "Сколько игроков собираем", example = "10")
        @NotNull(message = "Нужен размер комнаты.")
        MatchSize maxPlayers,

        @Schema(description = "Язык слов: свой дивизион либо английский. Не задан — язык своего дивизиона",
                example = "ru", nullable = true)
        DivisionLanguage gameLanguage) {
}
