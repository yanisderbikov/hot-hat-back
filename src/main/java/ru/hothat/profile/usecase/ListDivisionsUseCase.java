package ru.hothat.profile.usecase;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import ru.hothat.profile.api.dto.DivisionCatalogResponseDTO;
import ru.hothat.profile.api.dto.DivisionLanguage;
import ru.hothat.profile.api.dto.DivisionView;
import ru.hothat.util.Divisions;

import java.util.Comparator;
import java.util.List;

/**
 * Отдать справочник языковых дивизионов.
 *
 * <p>Открыт всем: список нужен экрану регистрации, где токена ещё нет.
 * Секрета в нём нет — это те же девять языков, что перечислены в интерфейсе;
 * смысл адреса в том, чтобы копия во фронтенде перестала быть источником
 * правды и не разъезжалась с серверной.
 */
@Service
public class ListDivisionsUseCase {

    @PreAuthorize("permitAll()")
    public DivisionCatalogResponseDTO run() {
        // Порядок задаём явно: Divisions.ALL собран через Map.copyOf,
        // а он порядок обхода не обещает — без сортировки список приезжал бы
        // каждый раз в новом порядке и ломал бы кеширование на клиенте.
        List<DivisionView> items = Divisions.ALL.values().stream()
                .sorted(Comparator.comparing(Divisions.Division::code))
                .map(division -> new DivisionView(
                        DivisionLanguage.fromWire(division.code()),
                        division.flag(),
                        division.locale(),
                        division.name(),
                        division.divisionName()))
                .toList();
        return new DivisionCatalogResponseDTO(items, null, items.size());
    }
}
