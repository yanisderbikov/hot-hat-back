package ru.hothat.rating.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.hothat.config.HotHatUser;
import ru.hothat.rating.api.dto.PlayerRankingPageResponseDTO;
import ru.hothat.rating.api.dto.PlayerRankingQueryDTO;
import ru.hothat.rating.api.dto.SeasonChampionQueryDTO;
import ru.hothat.rating.api.dto.SeasonChampionResponseDTO;
import ru.hothat.rating.api.dto.TeamRankingPageResponseDTO;
import ru.hothat.rating.api.dto.TeamRankingQueryDTO;
import ru.hothat.rating.usecase.GetSeasonChampionUseCase;
import ru.hothat.rating.usecase.ListPlayerRankingUseCase;
import ru.hothat.rating.usecase.ListTeamRankingUseCase;

/**
 * Сезонные рейтинговые таблицы.
 *
 * <p>Заменяет {@code POST /api/portal} с {@code action=ratings}, где одним
 * ответом ехали четыре разных предмета: таблица команд, таблица игроков,
 * чемпион сезона и список команд, в которых есть друзья. Страница рейтингов
 * ({@code portal.js:149}) перезапрашивала всё целиком при каждом переключении
 * вкладки — а показывала за раз что-то одно. Здесь у каждого предмета свой
 * адрес, а «команда с друзьями» перестала быть отдельным массивом и стала
 * признаком строки таблицы команд, где ей и место.
 *
 * <p>Уровень прав у класса один: таблицы открыты любому вошедшему игроку.
 *
 * <p>Старый адрес пока жив: фронтенд переедет на эти маршруты отдельно.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v2/rating")
@Tag(name = "Rating · таблицы", description = "Сезонные рейтинговые таблицы")
@SecurityRequirement(name = "Bearer")
public class RankingBoardController {

    private final ListTeamRankingUseCase listTeamRanking;
    private final ListPlayerRankingUseCase listPlayerRanking;
    private final GetSeasonChampionUseCase getSeasonChampion;

    @Operation(summary = "Таблица команд",
            description = "Команды сезона по убыванию очков. Место считает сервер: на вкладке "
                    + "«друзья» клиент показывает лишь часть строк, и нумеровать их по порядку "
                    + "показа нельзя. Непонятный сезон, режим или дивизион сервер заменяет "
                    + "умолчанием и говорит в поле selection, какую таблицу открыл.")
    @ApiResponse(responseCode = "200", description = "Таблица отдана; в несыгранном сезоне она пуста")
    @GetMapping("/teams")
    public ResponseEntity<TeamRankingPageResponseDTO> teams(
            @AuthenticationPrincipal HotHatUser user,
            @Valid @ParameterObject TeamRankingQueryDTO query) {
        return ResponseEntity.ok(listTeamRanking.run(user, query));
    }

    @Operation(summary = "Таблица игроков",
            description = "Игроки сезона по убыванию очков. Личная строка пишется тем же зачётом, "
                    + "что и командная, поэтому «таблица ещё не наполнилась» невозможно: в "
                    + "несыгранном сезоне она просто пуста.")
    @ApiResponse(responseCode = "200", description = "Таблица отдана; в несыгранном сезоне она пуста")
    @GetMapping("/players")
    public ResponseEntity<PlayerRankingPageResponseDTO> players(
            @AuthenticationPrincipal HotHatUser user,
            @Valid @ParameterObject PlayerRankingQueryDTO query) {
        return ResponseEntity.ok(listPlayerRanking.run(user, query));
    }

    @Operation(summary = "Чемпион сезона",
            description = "Одна строка для шапки страницы рейтингов. Победитель проставляется "
                    + "вручную при закрытии сезона, поэтому у текущего сезона его обычно нет — "
                    + "это не ошибка, а null в поле champion.")
    @ApiResponse(responseCode = "200", description = "Ответ отдан; champion пуст, если сезон не закрыт")
    @GetMapping("/champion")
    public ResponseEntity<SeasonChampionResponseDTO> champion(
            @AuthenticationPrincipal HotHatUser user,
            @Valid @ParameterObject SeasonChampionQueryDTO query) {
        return ResponseEntity.ok(getSeasonChampion.run(user, query));
    }
}
