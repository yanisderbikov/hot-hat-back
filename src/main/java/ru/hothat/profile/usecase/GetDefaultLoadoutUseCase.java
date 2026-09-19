package ru.hothat.profile.usecase;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.hothat.config.HotHatUser;
import ru.hothat.profile.api.dto.DefaultLoadoutResponseDTO;
import ru.hothat.sabotage.spi.SabotageArmoryPort;

import java.util.List;

/**
 * Показать обойму мемов учётной записи.
 *
 * <p>Отдаёт ровно то, что прочитает игра: обойма лежит строками слотов, и
 * порядок в ответе — это порядок слотов. Прежде она была jsonb-массивом, из
 * которого чтение по дороге выбрасывало пустые значения и повторы; отдать
 * сырое поле значило показать экрану арсенала одну обойму, а зарядить в
 * партию другую.
 *
 * <p>Транзакция только на чтение: заводить здесь нечего — слот появляется
 * при сохранении обоймы, а не при взгляде на неё.
 */
@Service
@RequiredArgsConstructor
public class GetDefaultLoadoutUseCase {

    private final SabotageArmoryPort armory;
    private final ProfileViews mapper;

    @PreAuthorize("hasRole('USER')")
    @Transactional(readOnly = true)
    public DefaultLoadoutResponseDTO run(HotHatUser user) {
        List<String> loadout = armory.loadout(user.uid());
        return new DefaultLoadoutResponseDTO(loadout, mapper.memeLoadout(loadout));
    }
}
