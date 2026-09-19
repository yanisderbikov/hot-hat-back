package ru.hothat.service.cleanup.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import ru.hothat.room.spi.RoomJanitorPort;
import ru.hothat.service.cleanup.RoomCleanupService;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Прежний адрес {@code /api/cleanup-rooms} поверх нового владельца.
 *
 * <p>Пороги, замок прохода и само удаление переехали в область комнаты
 * ({@code RoomJanitor}): комнату убирает тот, кто ей владеет. Здесь остался
 * перевод записей порта в карты — форму, которую ждут старый контроллер и
 * планировщик.
 *
 * <p>Класс жив до сноса старых контроллеров и уйдёт вместе с ними.
 */
@Service
@RequiredArgsConstructor
public class RoomCleanupServiceImpl implements RoomCleanupService {

    private final RoomJanitorPort janitor;

    @Override
    public Map<String, Object> cleanupOne(String roomId) {
        RoomJanitorPort.Cleanup cleanup = janitor.cleanupOne(roomId);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("roomId", cleanup.roomId());
        result.put("deleted", cleanup.deleted());
        result.put("reason", cleanup.reason());
        return result;
    }

    @Override
    public Map<String, Object> sweep() {
        RoomJanitorPort.Sweep sweep = janitor.sweep();
        List<Map<String, Object>> rooms = new ArrayList<>();
        for (RoomJanitorPort.Swept room : sweep.rooms()) {
            rooms.add(Map.of("roomId", room.roomId(), "reason", room.reason()));
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("skipped", sweep.skipped());
        result.put("checked", sweep.checked());
        result.put("deleted", rooms.size());
        result.put("rooms", rooms);
        return result;
    }
}
