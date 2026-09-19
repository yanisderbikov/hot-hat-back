package ru.hothat.game.usecase;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.hothat.config.HotHatUser;
import ru.hothat.game.api.dto.ReportClipResultRequestDTO;
import ru.hothat.game.api.dto.ReportedClipResultResponseDTO;
import ru.hothat.game.domain.ReplacementClip;
import ru.hothat.game.domain.weapon.ReplacementClipRules;
import ru.hothat.game.store.MatchSession;
import ru.hothat.game.store.MatchStore;

import java.time.Clock;

/**
 * Сообщить итог съёмки.
 *
 * <p>Присылает тот, кого снимали, — и это инвариант владения, живущий внутри
 * сценария (§7.4 плана): проверить его можно, только прочитав сам клип, а
 * предикат уровня класса читал бы партию вторично.
 *
 * <p>Неудачная съёмка стирается сразу: держать её значило бы занимать слот
 * заказчика впустую, а он о неудаче узнать не может — плёнка писалась не у него.
 */
@Service
@RequiredArgsConstructor
public class ReportClipResultUseCase {

    private final MatchStore matchStore;
    private final MatchViewAssembler views;
    private final Clock clock;

    @PreAuthorize("@gameAuthz.isPlayer(#roomId)")
    @Transactional
    public ReportedClipResultResponseDTO run(HotHatUser user, String roomId, String clipId,
                                             ReportClipResultRequestDTO request) {
        MatchSession session = matchStore.open(roomId);
        ReplacementClip clip = ReplacementClipRules.settle(session.state(), clipId, user.uid(),
                request.ready(), clock.millis());
        session.commit();
        return new ReportedClipResultResponseDTO(clip != null, views.clip(clip));
    }
}
