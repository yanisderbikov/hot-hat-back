package ru.hothat.game.store;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import ru.hothat.common.livekit.LiveKitClient;
import ru.hothat.game.domain.SabotageEvent;
import ru.hothat.game.port.SabotageBroadcastPort;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Рассылка диверсии по каналу данных LiveKit.
 *
 * <p>Отказ рассылки не отменяет диверсию: она уже записана в партию, и клиент
 * увидит её следующим снимком. Поэтому исключение здесь только пишется в лог.
 *
 * <p>Адресаты — из самого события ({@link SabotageEvent#audience()}): выстрел
 * летит всей комнате, съёмка Подмены — только снимающему и снимаемому. Иначе
 * пакет LiveKit отдавал бы третьим лицам то, что кадр канала от них прячет.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LiveKitSabotageBroadcastAdapter implements SabotageBroadcastPort {

    private final LiveKitClient liveKit;
    private final ObjectMapper objectMapper;

    @Override
    public void publish(String roomId, SabotageEvent event) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> payload = objectMapper.convertValue(event, LinkedHashMap.class);
            liveKit.sendSabotage(roomId, payload, event.audience());
        } catch (RuntimeException e) {
            log.warn("Рассылка диверсии в {} не прошла: {}", roomId, e.getMessage());
        }
    }
}
