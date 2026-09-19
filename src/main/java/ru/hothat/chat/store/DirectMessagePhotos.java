package ru.hothat.chat.store;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

/**
 * Дверь в {@code v2.direct_chat_photo}.
 *
 * <p>Картинки читаются пачкой по идентификаторам сообщений и только тогда,
 * когда их показывают: ради этого они и вынесены из строки сообщения.
 */
@Repository
interface DirectMessagePhotos extends JpaRepository<DirectMessagePhoto, Long> {

    List<DirectMessagePhoto> findByMessageIdIn(Collection<Long> messageIds);
}
