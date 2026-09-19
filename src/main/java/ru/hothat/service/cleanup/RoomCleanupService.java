package ru.hothat.service.cleanup;

import java.util.Map;

/** Уборка брошенных комнат: точечная по id и общая по расписанию. */
public interface RoomCleanupService {

    Map<String, Object> cleanupOne(String roomId);

    /** Полный проход; чаще раза в пять минут не запускается. */
    Map<String, Object> sweep();
}
