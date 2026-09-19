package ru.hothat.room.usecase;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.hothat.config.HotHatUser;
import ru.hothat.model.room.RoomPlayer;
import ru.hothat.repository.SaverRoom;
import ru.hothat.room.api.dto.DeviceStateResponseDTO;
import ru.hothat.room.api.dto.ReportDeviceStateRequestDTO;
import ru.hothat.util.Json;

/**
 * Сообщить, что у игрока с камерой и микрофоном.
 *
 * <p>Три места фронтенда писали эти два флага независимо друг от друга
 * ({@code app-core.js:5093}, {@code :12499}, {@code livekit.js:3175}), и
 * соседи по столу видели то, что успело записаться последним. Здесь оба флага
 * приходят вместе и записываются вместе.
 *
 * <p>Отметка времени — серверная. Клиентская была бы бесполезна: часы
 * участников расходятся на минуты, а по этой отметке решают, чьё состояние
 * свежее.
 *
 * <p>Замок комнаты здесь не берётся намеренно. Пишется одна своя строка, и
 * никто, кроме владельца, в неё не пишет; захватывать ради этого строку
 * комнаты значило бы выстраивать в очередь всех за столом на каждое включение
 * микрофона.
 */
@Service
@RequiredArgsConstructor
public class ReportDeviceStateUseCase {

    private final RoomAccessGuard roomAuthz;
    private final SaverRoom saverRoom;

    @PreAuthorize("hasRole('USER')")
    @Transactional
    public DeviceStateResponseDTO run(HotHatUser user, String roomId,
                                      ReportDeviceStateRequestDTO request) {
        RoomPlayer player = roomAuthz.requireMember(user, roomId);
        long now = System.currentTimeMillis();
        player.setCameraEnabled(Boolean.TRUE.equals(request.cameraEnabled()));
        player.setMicrophoneEnabled(Boolean.TRUE.equals(request.microphoneEnabled()));
        if (request.videoProvider() != null && !request.videoProvider().isBlank()) {
            player.setVideoProvider(Json.str(request.videoProvider(), 24));
        }
        player.setMediaReadyAt(now);
        player.setMediaRevision(now);
        // Отчёт об устройствах — это и присутствие: игрок, у которого только
        // что включилась камера, точно за экраном.
        player.setLastSeenAt(now);
        saverRoom.savePlayer(player);

        return new DeviceStateResponseDTO(
                Boolean.TRUE.equals(player.getCameraEnabled()),
                Boolean.TRUE.equals(player.getMicrophoneEnabled()),
                now);
    }
}
