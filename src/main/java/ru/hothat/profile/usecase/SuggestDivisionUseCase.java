package ru.hothat.profile.usecase;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import ru.hothat.profile.api.dto.DivisionLanguage;
import ru.hothat.profile.api.dto.DivisionSuggestionResponseDTO;
import ru.hothat.util.Divisions;

/**
 * Подсказать дивизион по стране запроса.
 *
 * <p>Страну сообщает обратный прокси-сервер: Cloudflare ставит
 * {@code CF-IPCountry}, свой балансировщик — {@code X-Country-Code}.
 * Первый заголовок главнее второго — его проставляет край сети, до которого
 * подделанному значению не добраться; браузер не спрашивают вовсе.
 */
@Service
public class SuggestDivisionUseCase {

    @PreAuthorize("permitAll()")
    public DivisionSuggestionResponseDTO run(String cloudflareCountry, String proxyCountry) {
        String country = cloudflareCountry != null && !cloudflareCountry.isBlank()
                ? cloudflareCountry
                : (proxyCountry == null ? "" : proxyCountry);
        country = country.trim().toUpperCase();
        return new DivisionSuggestionResponseDTO(
                country.isEmpty() ? null : country,
                DivisionLanguage.fromWire(Divisions.suggestedFromCountry(country)));
    }
}
