package ru.hothat.room.store;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;
import ru.hothat.model.room.RoomChatMessage;
import ru.hothat.model.room.RoomChatMessageId;

import java.util.List;
import java.util.Optional;

/**
 * Чат комнаты на чтение.
 *
 * <p>Свой репозиторий, потому что общий фасад {@code GetterRoom} чат читать не
 * умеет вовсе — он умеет только его писать. Так вышло не случайно: до сих пор
 * чат комнаты читал браузер, живой подпиской на {@code rooms/…/chat}
 * ({@code app-core.js:8795}), и серверу читать его было незачем.
 *
 * <p>Курсор — {@code createdAtMs}, а не смещение: в чате идущей партии
 * сообщения появляются между двумя страницами, и смещение съезжало бы. Под
 * запрос уже есть индекс {@code idx_room_chat_created (room_id,
 * created_at_ms)}, поэтому обе выборки — по одному проходу.
 *
 * <p>Запросы объявлены на JPQL, а не выведены из имени метода. Причина
 * техническая: у сообщения составной ключ {@code @IdClass(roomId, id)}, и
 * хвост {@code …IdDesc} в имени метода читается двусмысленно — то ли поле
 * {@code id}, то ли ключ целиком. Явный {@code order by} снимает вопрос.
 *
 * <p>Тип публичен, потому что им пользуется соседний пакет области. Наружу
 * области он не выходит.
 *
 * <p>Стереотипа над объявлением нет намеренно: бин интерфейсу-репозиторию
 * создаёт Spring Data сам, а {@code @Repository} из пакета стереотипов
 * столкнулся бы простым именем с базовым {@code Repository} Spring Data.
 */
public interface RoomChatMessages extends Repository<RoomChatMessage, RoomChatMessageId> {

    /**
     * Последние сообщения комнаты, от новых к старым.
     *
     * <p>Второй ключ сортировки нужен не для красоты: у сообщений, записанных
     * в одну миллисекунду, порядок иначе не определён, и одно из них
     * приезжало бы то на первой странице, то на второй.
     */
    @Query("select m from RoomChatMessage m where m.roomId = :roomId "
            + "order by m.createdAtMs desc, m.id desc")
    List<RoomChatMessage> latest(@Param("roomId") String roomId, Pageable page);

    /** Страница вглубь: всё, что старше курсора. */
    @Query("select m from RoomChatMessage m where m.roomId = :roomId "
            + "and m.createdAtMs < :beforeCreatedAtMs "
            + "order by m.createdAtMs desc, m.id desc")
    List<RoomChatMessage> before(@Param("roomId") String roomId,
                                 @Param("beforeCreatedAtMs") long beforeCreatedAtMs,
                                 Pageable page);

    @Query("select m from RoomChatMessage m where m.roomId = :roomId and m.id = :messageId")
    Optional<RoomChatMessage> find(@Param("roomId") String roomId, @Param("messageId") String messageId);
}
