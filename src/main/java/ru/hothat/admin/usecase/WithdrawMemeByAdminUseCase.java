package ru.hothat.admin.usecase;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import ru.hothat.config.HotHatUser;
import ru.hothat.media.spi.MemeModerationPort;

/**
 * Снять мем с публикации по сигналу.
 *
 * <p>Заменяет {@code POST /api/admin} с {@code action=delete_meme_alert} —
 * действие, которое админка зовёт из карточки пожаловавшегося игрока.
 *
 * <p>От игроцкого «удалить свой мем» отличается правом: там автор убирает
 * своё, здесь администратор — чужое. Сценарии разные, и слить их в один с
 * булевым флагом значило бы держать оба правила в одном {@code if}.
 *
 * <p>Библиотеку правит её область: здесь остаются право и запись в журнал.
 * Правила самой библиотеки — защита встроенного мема и 404 у несуществующего —
 * живут за портом, там же, где карточка.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WithdrawMemeByAdminUseCase {

    private final MemeModerationPort memes;

    @PreAuthorize("hasRole('ADMIN')")
    public void run(HotHatUser admin, String memeId) {
        memes.withdraw(memeId);
        log.info("Мем {} снят администратором {}", memeId, admin.uid());
    }
}
