package ru.hothat.profile.usecase;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.hothat.config.HotHatUser;
import ru.hothat.profile.store.ProfileStore;

/**
 * Снять отметку о комнате: игрок больше нигде не играет.
 *
 * <p>Отдельный адрес, а не запись {@code roomId: null} в тот же PUT.
 * Стирание метки — это отказ от автоматического возвращения в партию, и
 * вызывается оно в других местах: конец партии, выход из комнаты, неудачная
 * попытка вернуться. Телу запроса при этом сказать нечего.
 *
 * <p>Отметка присутствия обновляется и здесь: снятая метка не должна
 * выглядеть старее, чем есть.
 */
@Service
@RequiredArgsConstructor
public class ClearMyActiveRoomUseCase {

    private final ProfileStore profiles;

    @PreAuthorize("hasRole('USER')")
    @Transactional
    public void run(HotHatUser user) {
        profiles.setActiveRoom(user.uid(), null);
    }
}
