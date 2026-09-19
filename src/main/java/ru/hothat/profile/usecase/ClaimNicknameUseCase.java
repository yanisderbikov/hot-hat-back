package ru.hothat.profile.usecase;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.hothat.config.HotHatUser;
import ru.hothat.profile.api.dto.ClaimNicknameRequestDTO;
import ru.hothat.profile.api.dto.ClaimedNicknameResponseDTO;
import ru.hothat.profile.store.ProfileStore;
import ru.hothat.room.spi.RoomNicknamePort;

/**
 * Занять ник за собой.
 *
 * <p>Ник уникален: занятый чужой учётной записью отвечает 409
 * {@code NICKNAME_TAKEN}. Проверка стоит и в сценарии — ради понятной
 * ошибки, — но последнее слово за уникальным индексом {@code nickname_key}:
 * два одновременных переименования в одно имя до него доходили оба.
 *
 * <p>Копии имени в комнатах подновляются тут же и одной транзакцией. Их
 * четыре — место игрока, место зрителя, карта имён партии и подписи ролей
 * хода, — и все четыре принадлежат ещё не переехавшей области комнаты;
 * поэтому обход остался, но делает его владелец таблиц через свой порт.
 */
@Service
@RequiredArgsConstructor
public class ClaimNicknameUseCase {

    private final ProfileStore profiles;
    private final RoomNicknamePort roomNicknames;

    @PreAuthorize("hasRole('USER')")
    @Transactional
    public ClaimedNicknameResponseDTO run(HotHatUser user, ClaimNicknameRequestDTO request) {
        String nickname = profiles.rename(user.uid(), request.nickname());
        roomNicknames.renameEverywhere(user.uid(), nickname);
        return new ClaimedNicknameResponseDTO(nickname);
    }
}
