package ru.hothat.game.usecase;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import ru.hothat.game.api.dto.WeaponCatalogResponseDTO;
import ru.hothat.game.api.dto.WeaponView;
import ru.hothat.game.domain.LoadoutRules;
import ru.hothat.game.domain.Weapon;
import ru.hothat.game.domain.WeaponRegistry;

import java.util.List;

/**
 * Отдать каталог арсенала.
 *
 * <p>Существует затем, чтобы копия этой таблицы во фронте
 * ({@code app-core.js:356}) перестала быть нужна: она уже разошлась с
 * серверной, и расхождение роняло начисление наград.
 *
 * <p>Ни базы, ни партии здесь нет — каталог неизменен, поэтому и транзакции
 * этому сценарию не нужно.
 */
@Service
public class GetWeaponCatalogUseCase {

    @PreAuthorize("hasRole('USER')")
    public WeaponCatalogResponseDTO run() {
        List<WeaponView> weapons = WeaponRegistry.all().stream().map(GetWeaponCatalogUseCase::view).toList();
        return new WeaponCatalogResponseDTO(weapons, WeaponRegistry.baseArsenal(), WeaponRegistry.ammoKeys(),
                WeaponRegistry.COOLDOWN_MS, LoadoutRules.SIZE);
    }

    private static WeaponView view(Weapon weapon) {
        return new WeaponView(weapon.type(), weapon.ammoKey(), weapon.baseAmmo(), weapon.durationMs(),
                weapon.lock().name(), weapon.advanced(), weapon.ownerOnly(),
                weapon.minimumTurnRemainingMs(), weapon.cooldownExempt(), weapon.allowedDuringReplacement());
    }
}
