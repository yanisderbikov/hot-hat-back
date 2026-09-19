package ru.hothat.auth.usecase;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import ru.hothat.admin.spi.BanPort;
import ru.hothat.auth.api.dto.MyBanStateResponseDTO;
import ru.hothat.config.HotHatUser;

/**
 * Сказать игроку, заблокирован ли он и почему.
 *
 * <p>Заменяет клиентскую подписку на {@code bans/{uid}}: страница узнавала о
 * бане по появлению документа и молча выбрасывала человека на главную. Гостю
 * этот путь был закрыт вовсе, а гость сидит в превью комнаты — то есть ровно
 * там, где банят.
 *
 * <p><b>Ветка {@code banned=true} по-прежнему почти недостижима, и это честно
 * названо.</b> Бан поднимает поколение токенов, поэтому выданный ранее
 * access-токен перестаёт приниматься тут же, а разбор токена отвергает
 * запрос раньше проверки ролей. Достижима она ровно в одном окне: между
 * ответом на этот адрес и следующим обновлением пары — то есть когда клиент
 * спрашивает состояние, уже получив 401 на что-то другое и ещё не выйдя.
 * Сценарий оставлен, потому что факт он теперь читает из единственного
 * источника — строки {@code v2.user_ban}, — а не из флага, которого больше
 * нет.
 *
 * <p>Кто заблокировал, наружу не уходит: это имя администратора, и знать его
 * заблокированному незачем.
 */
@Service
@RequiredArgsConstructor
public class GetMyBanStateUseCase {

    private final BanPort bans;

    @PreAuthorize("hasAnyRole('USER','GUEST')")
    public MyBanStateResponseDTO run(HotHatUser user) {
        return bans.activeBan(user.uid())
                .map(ban -> new MyBanStateResponseDTO(true, ban.reason(), ban.bannedAtMs()))
                .orElseGet(() -> new MyBanStateResponseDTO(false, null, null));
    }
}
