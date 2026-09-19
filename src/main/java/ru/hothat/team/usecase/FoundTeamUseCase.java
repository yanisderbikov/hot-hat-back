package ru.hothat.team.usecase;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.hothat.config.ApiException;
import ru.hothat.config.HotHatUser;
import ru.hothat.friend.spi.FriendshipPort;
import ru.hothat.profile.spi.PlayerCardPort;
import ru.hothat.profile.api.dto.DivisionLanguage;
import ru.hothat.team.api.dto.FoundTeamRequestDTO;
import ru.hothat.team.api.dto.FoundedTeamResponseDTO;
import ru.hothat.team.api.dto.TeamStatus;
import ru.hothat.team.store.TeamStore;
import ru.hothat.util.Divisions;
import ru.hothat.util.Ids;

/**
 * Основать рейтинговую команду и позвать в неё напарника.
 *
 * <p>Предусловия переехали сюда из старого движка и стоят в том же порядке:
 * оба не должны быть в команде, имя должно быть свободно, напарник — быть
 * другом того же дивизиона и не быть самим собой. Держать их здесь важно:
 * область сама решает, кого пускает в свои таблицы, и второй формулы допуска
 * нет.
 *
 * <p>«Уже в команде» спрашивается у состава, а не у карточки игрока. Раньше
 * этот факт писали два места, и расхождение между ними означало команду,
 * которую видит один участник и не видит другой. Последнее слово всё равно за
 * уникальным индексом {@code ux_ranked_team_member_player}: два основания,
 * пришедшие одновременно, до него доходили оба.
 */
@Service
@RequiredArgsConstructor
public class FoundTeamUseCase {

    /** Ник и дивизион обоих — у карточки игрока, а не у оболочки учётки. */
    private final PlayerCardPort cards;
    private final TeamStore teams;
    private final FriendshipPort friendships;

    @PreAuthorize("hasRole('USER')")
    @Transactional
    public FoundedTeamResponseDTO run(HotHatUser user, FoundTeamRequestDTO request) {
        PlayerCardPort.Card owner = cards.card(user.uid())
                .orElseThrow(() -> ApiException.of("PLAYER_NOT_FOUND", 404));
        if (teams.teamOf(user.uid()).isPresent()) {
            throw ApiException.of("ALREADY_IN_TEAM", 409);
        }
        String name = request.name().trim();
        String partnerNickname = request.partnerNickname().trim();
        // Форму имени и ника проверила схема запроса; здесь остаётся то, чего
        // она проверить не может, — совпадение с самим собой.
        if (Ids.key(partnerNickname).equals(Ids.key(owner.nickname()))) {
            throw ApiException.of("PARTNER_SELF");
        }
        if (teams.nameTaken(name)) {
            throw ApiException.of("TEAM_NAME_TAKEN", 409);
        }

        String inviteeUid;
        try {
            inviteeUid = cards.requireUidByNickname(partnerNickname);
        } catch (ApiException e) {
            // «Игрока с таким ником нет» звучит понятнее в терминах команды:
            // человек искал напарника, а не абстрактный профиль.
            if ("PLAYER_NOT_FOUND".equals(e.getCode())) {
                throw ApiException.of("PARTNER_NOT_FOUND", 404);
            }
            throw e;
        }
        PlayerCardPort.Card partner = cards.card(inviteeUid)
                .orElseThrow(() -> ApiException.of("PARTNER_NOT_FOUND", 404));
        if (teams.teamOf(inviteeUid).isPresent()) {
            throw ApiException.of("PARTNER_ALREADY_IN_TEAM", 409);
        }
        String divisionLanguage = Divisions.normalize(owner.divisionLanguage());
        if (!divisionLanguage.equals(Divisions.normalize(partner.divisionLanguage()))) {
            throw ApiException.of("TEAM_DIVISION_MISMATCH", 409);
        }
        // Дружбу спрашиваем у её владельца, а не у таблицы: у области дружбы
        // есть свой порт, и второго источника этого факта не существует —
        // экран друзей тоже читает только его.
        if (!friendships.areFriends(user.uid(), inviteeUid)) {
            throw ApiException.of("PARTNER_MUST_BE_FRIEND", 409);
        }

        TeamStore.Founded founded = teams.found(
                user.uid(), inviteeUid, name, divisionLanguage, request.logoDataUrl());
        return new FoundedTeamResponseDTO(
                founded.teamId(),
                name,
                founded.inviteId(),
                inviteeUid,
                DivisionLanguage.fromWire(divisionLanguage),
                // Свежая команда всегда ждёт ответа: подтверждает её напарник.
                TeamStatus.PENDING);
    }
}
