package ru.hothat.team.usecase;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import ru.hothat.team.api.dto.GameMode;
import ru.hothat.team.api.dto.PreflightIntent;
import ru.hothat.team.api.dto.PreflightParticipantView;
import ru.hothat.team.api.dto.PreflightSessionView;
import ru.hothat.team.store.TeamStore;

import java.util.ArrayList;
import java.util.List;

/**
 * Собирает снимок проверки готовности из строк хранилища.
 *
 * <p>Заменил переходный переводчик сырых карт старого движка: строки
 * {@code v2.team_preflight_participant} уже типизированы, и разбирать три
 * параллельных jsonb с uid в ключе больше не нужно — именно там ключи
 * расходились, и снимок получался про разных людей.
 *
 * <p>Свежесть связи считается здесь, а не хранится: признак протухает за 25
 * секунд, и «свежий» — это не состояние строки, а её возраст на момент
 * ответа. Считает его сервер, потому что у клиента часы уезжают.
 *
 * <p>Состав приходит из команды, а не из самой проверки: участник, который
 * ещё ничего о себе не сообщил, обязан попасть в снимок — иначе напарник не
 * увидит, кого именно ждёт.
 */
@Component
@RequiredArgsConstructor
public class PreflightAssembler {

    public PreflightSessionView session(TeamStore.PreflightRow preflight, TeamStore.TeamRow team,
                                        TeamProfileDirectory.Snapshot profiles, long nowMs) {
        List<String> uids = team.memberUids();
        List<PreflightParticipantView> participants = new ArrayList<>(uids.size());
        boolean bothMedia = !uids.isEmpty();
        boolean bothReady = !uids.isEmpty();
        for (String uid : uids) {
            TeamStore.ParticipantRow row = preflight.participant(uid);
            boolean mediaOk = row != null && row.freshMedia(nowMs, TeamReadLimits.MEDIA_FLAG_TTL_MS);
            // Готовность без свежей связи не считается: играть вслепую нельзя,
            // и правило это одно на запись и на чтение.
            boolean ready = mediaOk && row.ready();
            bothMedia &= mediaOk;
            bothReady &= ready;
            participants.add(new PreflightParticipantView(uid, profiles.nickname(uid), mediaOk, ready));
        }
        return new PreflightSessionView(
                preflight.teamId(),
                PreflightIntent.fromWire(preflight.intent()),
                GameMode.fromWire(preflight.gameMode()),
                preflight.requestedRoomId(),
                preflight.initiatorUid(),
                participants,
                bothMedia,
                bothReady,
                bothMedia && bothReady,
                preflight.targetRoomId(),
                preflight.roomReady(),
                preflight.searchStarted(),
                preflight.searchCount(),
                preflight.failed(),
                preflight.startedAtMs(),
                preflight.updatedAtMs(),
                preflight.expiresAtMs());
    }
}
