package ru.hothat.team.usecase;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.hothat.config.ApiException;
import ru.hothat.config.HotHatUser;
import ru.hothat.room.spi.RoomDirectoryPort;
import ru.hothat.realtime.spi.RealtimeChangeBus;
import ru.hothat.auth.spi.AccountPort;
import ru.hothat.profile.spi.PlayerCardPort;
import ru.hothat.sabotage.spi.SabotageArmoryPort;
import ru.hothat.team.api.dto.GameMode;
import ru.hothat.team.api.dto.PreflightIntent;
import ru.hothat.team.api.dto.PreflightSessionResponseDTO;
import ru.hothat.team.api.dto.StartPreflightRequestDTO;
import ru.hothat.team.store.TeamStore;
import ru.hothat.util.Divisions;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Начать проверку готовности пары перед рейтинговой игрой.
 *
 * <p>Первый шаг отдельного жизненного цикла: старт, отчёты о связи, готовность,
 * отмена и чтение — пять разных событий с разными правами и разными
 * последствиями, а не одно действие с полем-переключателем.
 *
 * <p>Предусловия стоят здесь целиком, как и у основания команды: у обоих
 * участников должна быть заряжена обойма мемов, в режиме диверсий у обоих
 * должен оставаться лимит бесплатных партий, а при замысле {@code room}
 * названная комната должна быть открытой рейтинговой комнатой того же режима
 * и того же дивизиона. Карточки обоих читаются одной пачкой — раньше на
 * каждого участника уходило отдельное чтение, и делал его каждый из четырёх
 * сценариев префлайта.
 */
@Service
@RequiredArgsConstructor
public class StartPreflightUseCase {

    private final TeamMembershipGuard teamAuthz;
    private final TeamStore teams;
    /** Обойма и квота диверсий принадлежат своей области, а не оболочке учётки. */
    private final SabotageArmoryPort armory;
    /** Дивизион игрока — у карточки профиля. */
    private final PlayerCardPort cards;
    /** Почта участника — у области личности: квота диверсий смотрит на владельца. */
    private final AccountPort accounts;
    /** Справку о комнате даёт её область, а не её таблица. */
    private final RoomDirectoryPort rooms;
    private final TeamProfileDirectory profileDirectory;
    private final PreflightAssembler assembler;
    /**
     * Канал проверки обязан узнать о новой сессии от того, кто её создал:
     * старый мостик переводил в события записи документного шлюза, а этот
     * сценарий туда больше не пишет. Без вызова напарник увидел бы
     * приглашение к игре только со следующим опросом.
     */
    private final RealtimeChangeBus realtime;

    @PreAuthorize("hasRole('USER')")
    @Transactional
    public PreflightSessionResponseDTO run(HotHatUser user, StartPreflightRequestDTO request) {
        TeamStore.TeamRow team = teamAuthz.requireActiveTeam(user, cards.card(user.uid()).orElse(null));
        List<String> uids = team.memberUids();

        // Обоймы обоих участников — одним запросом к области диверсий:
        // спрашивать их по одному значило бы читать в цикле по составу.
        // Правило «ровно пять» проверяет владелец обоймы, а не эта область.
        armory.requireLoadouts(uids);
        if (request.gameMode() == GameMode.SABOTAGE) {
            requireSabotageAllowance(uids);
        }
        if (request.intent() == PreflightIntent.ROOM) {
            requireOpenRankedRoom(request.roomId(), request.gameMode(), team.divisionLanguage());
        }

        long now = System.currentTimeMillis();
        teams.startPreflight(
                team.teamId(),
                request.intent().wireValue(),
                request.gameMode().wireValue(),
                request.roomId(),
                user.uid(),
                Instant.ofEpochMilli(now + TeamReadLimits.PREFLIGHT_TTL_MS));

        TeamStore.PreflightRow preflight = teams.preflight(team.teamId())
                // Только что записанная проверка обязана читаться обратно.
                // Отдать 201 с пустым телом значило бы сказать «создано» про
                // то, чего нет.
                .orElseThrow(() -> ApiException.of("PREFLIGHT_REQUIRED", 409));
        realtime.preflightChanged(team.teamId());
        TeamProfileDirectory.Snapshot profiles = profileDirectory.load(uids);
        return new PreflightSessionResponseDTO(assembler.session(preflight, team, profiles, now));
    }

    /**
     * Пять бесплатных партий с диверсиями считаются каждому отдельно, и в
     * рейтинговую пару выходят двое: отказ одного закрывает партию обоим.
     */
    private void requireSabotageAllowance(List<String> uids) {
        // Учётки обоих — одним запросом: почта нужна квоте ради единственной
        // проверки «это владелец сервиса», и спрашивается она у области
        // личности, а не читается по одной внутри цикла.
        Map<String, AccountPort.Account> members = accounts.accounts(uids);
        for (String uid : uids) {
            AccountPort.Account member = members.get(uid);
            if (!armory.entitlement(uid, member == null ? null : member.email()).allowed()) {
                throw ApiException.of("TEAMMATE_SABOTAGE_LIMIT_REACHED", 402);
            }
        }
    }

    /**
     * Замысел {@code room} обязан указывать на живую рейтинговую комнату того
     * же режима и дивизиона. Иначе пара уезжала бы к случайным соперникам —
     * ровно та ошибка, ради которой замысел стал перечислением.
     */
    private void requireOpenRankedRoom(String roomId, GameMode mode, String divisionLanguage) {
        // Справку даёт область комнаты: разбирать её строку на колонки отсюда
        // значило бы знать, что «открыта» — это три поля сразу.
        RoomDirectoryPort.RoomBrief room = rooms.find(roomId).orElse(null);
        if (room == null || !room.open() || !room.ranked()) {
            throw ApiException.of("RANKED_ROOM_UNAVAILABLE", 409);
        }
        if (!mode.wireValue().equals(room.gameMode())) {
            throw ApiException.of("RANKED_ROOM_MODE_MISMATCH", 409);
        }
        if (!Divisions.normalize(room.divisionLanguage()).equals(Divisions.normalize(divisionLanguage))) {
            throw ApiException.of("RANKED_ROOM_DIVISION_MISMATCH", 409);
        }
    }
}
