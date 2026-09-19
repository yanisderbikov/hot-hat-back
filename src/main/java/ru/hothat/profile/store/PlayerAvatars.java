package ru.hothat.profile.store;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Аватары в таблице {@code v2.player_avatar}.
 *
 * <p>Отдельная таблица, а не колонка карточки: аватар — до 90 килобайт, и
 * пока он лежал в одной строке с ником и присутствием, отметка «я в сети»
 * переписывала его шесть раз в минуту.
 */
@Repository
interface PlayerAvatars extends JpaRepository<PlayerAvatar, UUID> {

    /** Аватары названных игроков разом: список друзей — один запрос. */
    List<PlayerAvatar> findByPlayerIdIn(Collection<UUID> playerIds);
}
