package ru.hothat.team.usecase;

import org.springframework.stereotype.Component;
import ru.hothat.profile.api.dto.DivisionLanguage;
import ru.hothat.team.api.dto.TeamCardView;
import ru.hothat.team.api.dto.TeamMemberView;
import ru.hothat.team.api.dto.TeamPartnerView;
import ru.hothat.team.api.dto.TeamStatus;
import ru.hothat.team.store.TeamStore;
import ru.hothat.util.Divisions;

import java.util.ArrayList;
import java.util.List;

/**
 * Собирает карточку команды из строк хранилища и уже прочитанных профилей.
 *
 * <p>Живёт отдельно, потому что карточку отдают два сценария — своя команда и
 * лобби, — и раньше её собирал приватный {@code teamRow(...)} внутри движка:
 * форма ответа была описана в теле метода и наружу уезжала картой без схемы.
 *
 * <p>Профили сюда приходят готовыми, а не читаются здесь: иначе сборщик,
 * вызванный в цикле, снова развёл бы веер запросов, ради устранения которого
 * заведён {@link TeamProfileDirectory}. По той же причине логотип приходит
 * отдельным значением: он весит до 280 КБ, лежит своей строкой, и просить его
 * должен тот экран, который его рисует.
 */
@Component
public class TeamCardAssembler {

    /** Карточка команды: состав с никами и аватарами, дивизион, состояние. */
    public TeamCardView card(TeamStore.TeamRow team, String logoDataUrl,
                             TeamProfileDirectory.Snapshot profiles) {
        List<TeamMemberView> members = new ArrayList<>(team.members().size());
        for (TeamStore.MemberRow member : team.members()) {
            members.add(new TeamMemberView(
                    member.uid(),
                    profiles.nickname(member.uid()),
                    profiles.avatarDataUrl(member.uid())));
        }
        return new TeamCardView(
                team.teamId(),
                team.name(),
                // Владелец команды перестал быть отдельной колонкой рядом со
                // списком участников: это участник с ролью капитана.
                team.captainUid(),
                members,
                DivisionLanguage.fromWire(Divisions.normalize(team.divisionLanguage())),
                blankToNull(logoDataUrl),
                TeamStatus.fromWire(team.status()),
                team.createdAtMs());
    }

    /**
     * Напарник — участник команды, который не есть сам игрок.
     *
     * <p>null у команды, где второго участника не нашлось. Такого быть не
     * должно: пара подтверждается вторым человеком. Но чинить здесь нечего, а
     * падать из-за неполного состава карточка не должна.
     */
    public TeamPartnerView partner(TeamStore.TeamRow team, String selfUid,
                                   TeamProfileDirectory.Snapshot profiles) {
        for (TeamStore.MemberRow member : team.members()) {
            if (member.uid() == null || member.uid().equals(selfUid)) {
                continue;
            }
            return new TeamPartnerView(
                    member.uid(),
                    profiles.nickname(member.uid()),
                    profiles.avatarDataUrl(member.uid()),
                    profiles.divisionLanguage(member.uid()));
        }
        return null;
    }

    /** Логотипа может не быть вовсе; в контракте это null, а не пустая строка. */
    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
