package ru.hothat.game.store;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;
import ru.hothat.model.room.Room;

import java.util.Optional;

/**
 * Доступ к строке партии с блокировкой на время сценария.
 *
 * <p>Блокировка нужна ровно затем, чтобы два быстрых нажатия «Угадали» не
 * прочитали одно и то же слово и не записали за него два очка. В браузере эту
 * роль играли транзакции хранилища документов — они были сериализуемыми, и
 * клиент вдобавок выстраивал нажатия в очередь. На сервере с обычным уровнем
 * изоляции ни того ни другого нет: оба запроса прочитали бы одно состояние, и
 * второй записал бы поверх первого.
 *
 * <p>Блокировка на строку комнаты, а не на партию целиком: партия в комнате
 * одна, и все её операции всё равно правят эту строку.
 */
@org.springframework.stereotype.Repository
public interface MatchRoomRepo extends Repository<Room, String> {

    /**
     * Прочитать строку партии и удержать её до конца транзакции.
     *
     * <p>Требует открытой транзакции: без неё блокировка снялась бы сразу же и
     * смысла в ней не было бы никакого.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from Room r where r.id = :roomId")
    Optional<Room> lock(@Param("roomId") String roomId);

    /** Прочитать без блокировки: для ответов и предикатов прав. */
    @Query("select r from Room r where r.id = :roomId")
    Optional<Room> read(@Param("roomId") String roomId);
}
