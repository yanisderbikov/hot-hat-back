package ru.hothat.service.testbots;

import ru.hothat.config.HotHatUser;

import java.util.Map;

/** Тестовая комната с ботами: только для админа, режим отладки интерфейса. */
public interface TestBotsService {

    Map<String, Object> setup(HotHatUser user, String roomId);

    /** Один шаг раннера: боты ходят, стреляют диверсиями и болтают в чате. */
    Map<String, Object> tick(HotHatUser user, String roomId);

    Map<String, Object> stop(HotHatUser user, String roomId);

    Map<String, Object> fireOwnerFart(HotHatUser user, String roomId, Map<String, Object> body);
}
