package ru.hothat.game.usecase;

import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.hothat.config.ApiException;
import ru.hothat.config.HotHatUser;
import ru.hothat.game.api.dto.SabotageEventResponseDTO;
import ru.hothat.game.api.dto.UseWeaponRequestDTO;
import ru.hothat.game.domain.MatchPlayer;
import ru.hothat.game.domain.WeaponType;
import ru.hothat.game.domain.weapon.MemeMedia;
import ru.hothat.game.domain.weapon.SabotageCommand;
import ru.hothat.game.domain.weapon.SabotageEngine;
import ru.hothat.game.domain.weapon.SabotageOutcome;
import ru.hothat.game.port.MemeCatalogPort;
import ru.hothat.game.store.MatchSession;
import ru.hothat.game.store.MatchStore;

/**
 * Применить оружие.
 *
 * <p>Один сценарий на все тринадцать видов. Ветвления по виду оружия здесь
 * нет: его делает реестр обработчиков в домене, а контроллер и вовсе видит
 * только перечисление. Раньше на этом месте был {@code switch} по строке из
 * тела запроса и тринадцать с лишним мест правки на каждое новое оружие
 * (замечание F2 аудита).
 *
 * <p>Метаданные ролика читаются до входа в домен: правилам нужна только
 * длительность, а библиотека мемов — чужая область за сетью.
 */
@Service
@RequiredArgsConstructor
public class UseWeaponUseCase {

    private final MatchStore matchStore;
    private final SabotageEngine sabotage;
    private final MemeCatalogPort memes;
    private final MatchViewAssembler views;
    private final ApplicationEventPublisher events;

    @PreAuthorize("@gameAuthz.isPlayer(#roomId)")
    @Transactional
    public SabotageEventResponseDTO run(HotHatUser user, String roomId, UseWeaponRequestDTO request) {
        MemeMedia media = null;
        if (request.type() == WeaponType.MEME) {
            media = memes.find(request.memeId() == null ? "" : request.memeId().trim())
                    .orElseThrow(() -> ApiException.of("MEME_NOT_FOUND", 404));
        }
        MatchSession session = matchStore.open(roomId);
        MatchPlayer attacker = session.player(user.uid());
        SabotageCommand command = new SabotageCommand(request.type(), user.uid(),
                request.memeId() == null ? null : request.memeId().trim(),
                request.clipId() == null ? null : request.clipId().trim(),
                request.x(), request.y(), media, user.owner());
        SabotageOutcome outcome = sabotage.fire(session.state(), attacker, command);
        session.commit();
        // Рассылка — после фиксации: до неё диверсии ещё не случилось.
        events.publishEvent(new MatchEvents.SabotageApplied(roomId, outcome.event()));
        return new SabotageEventResponseDTO(views.event(outcome.event()), views.arsenal(attacker));
    }
}
