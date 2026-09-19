package ru.hothat.chat.store;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * Одно сообщение личной переписки.
 *
 * <p>Идентификатор — он же курсор истории. Именно поэтому он числовой и его
 * выдаёт база: она выдаёт его строго возрастающим, а отметка времени у двух
 * сообщений может совпасть до миллисекунды, и тогда «последнее» перестало бы
 * быть одним.
 *
 * <p>{@link #kind} называет сервер, а не восстанавливает клиент, заглядывая в
 * {@code attachment.kind}: сегодня это ветвление повторено в трёх местах
 * фронтенда, и каждое ошибается по-своему. Набор значений закрыт ограничением
 * базы, а согласие вида с наполнением — вторым ограничением: у записи
 * заполнена запись, у приглашения — приглашение.
 *
 * <p>Получателя здесь нет: в переписке двое, второй — тот из пары, кто не
 * отправитель. Картинка лежит отдельной строкой
 * ({@link DirectMessagePhoto}), иначе чтение истории тащило бы из TOAST все
 * картинки страницы даже при рисовании списка превью.
 */
@Entity
@Table(name = "direct_chat_message", schema = "v2")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
class DirectMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "chat_id", nullable = false, updatable = false)
    private Long chatId;

    @Column(name = "sender_player_id", nullable = false, updatable = false)
    private UUID senderPlayerId;

    /** text | image | recording | room_invite — набор закрыт ограничением базы. */
    @Column(nullable = false, length = 16)
    private String kind;

    /** 800 символов — предел, которым режет текст сегодняшний движок. */
    @Column(length = 800)
    private String body;

    /** Запись, которой поделились. Внешнего ключа нет: таблицы записей ещё нет. */
    @Column(name = "shared_recording_id")
    private UUID sharedRecordingId;

    /** Приглашение в комнату. Внешнего ключа нет по той же причине. */
    @Column(name = "room_invite_id")
    private UUID roomInviteId;

    @Builder.Default
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();
}
