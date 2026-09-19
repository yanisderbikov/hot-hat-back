package ru.hothat.realtime.usecase;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.hothat.machine.api.dto.RecorderRoomStateResponseDTO;
import ru.hothat.machine.api.dto.RecorderSceneState;
import ru.hothat.machine.api.dto.RecorderTeamView;
import ru.hothat.machine.usecase.ReadRecorderRoomStateUseCase;
import ru.hothat.machine.usecase.RecorderSceneAssembler;
import ru.hothat.realtime.api.dto.RecorderMirrorView;
import ru.hothat.repository.GetterRoom;

import java.util.List;

/**
 * Показать сцену странице записи — канал
 * {@code /ws/v2/machine/recorder/rooms/{roomId}}.
 *
 * <p>Сценарий один и тот же для обоих кадров. Зеркало собирает тот же
 * {@link ReadRecorderRoomStateUseCase}, что отвечает на
 * {@code GET …/room-state}, — вместе со всеми его правилами: сверкой номера
 * партии, отказом при выключенной записи и признаком устаревшей сцены. Это
 * важно вдвойне: запасной путь рекордера ходит по HTTP, и если бы канал
 * собирал зеркало сам, видео, снятое по запасному пути, отличалось бы от
 * снятого по каналу.
 *
 * <p>Составы добираются отдельно, потому что зеркало комнаты их не несёт:
 * счёт команд лежит своими строками. Один запрос по комнате, не в цикле.
 *
 * <p>Право — роль рекордера, выданная по подписи съёмки. Подпись считается от
 * пары «комната и номер партии», поэтому подписью от чужой съёмки эту не
 * открыть; проверяет её то же рукопожатие, что и адреса HTTP машинной
 * поверхности.
 */
@Service
@RequiredArgsConstructor
public class StreamRecorderChannelUseCase {

    private final ReadRecorderRoomStateUseCase roomState;
    private final RecorderSceneAssembler assembler;
    private final GetterRoom getterRoom;

    @PreAuthorize("hasRole('RECORDER')")
    @Transactional(readOnly = true)
    public RecorderMirrorView run(String roomId, int gameNumber) {
        RecorderRoomStateResponseDTO mirror = roomState.run(roomId, gameNumber);
        // У устаревшей сцены составы не нужны: рисовать по ним нечего, а
        // читать их значило бы платить запросом за пустой кадр.
        List<RecorderTeamView> teams = mirror.state() == RecorderSceneState.LIVE
                ? assembler.teams(getterRoom.getTeams(roomId))
                : List.of();
        return new RecorderMirrorView(mirror.state(), mirror.roomId(), mirror.gameNumber(),
                mirror.phase(), mirror.roomState(), teams, mirror.serverNowMs());
    }
}
