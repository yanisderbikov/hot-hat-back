package ru.hothat.team.spi;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Что область команды отвечает соседям.
 *
 * <p>Здесь только проекции: кто в какой команде, как она называется и чем
 * помечена. Спрашивают их таблица рейтинга, карточка игрока и подбор
 * соперников — всем троим нужно имя и состав, своих копий у них больше нет.
 * Готовность пары живёт в отдельном порту: у неё есть предусловия, а значит и
 * зависимость от карточки игрока, и держать их в одном бине с проекциями
 * значило бы замкнуть кольцо профиль → команда → профиль.
 *
 * <p>Порт, а не общий репозиторий: соседняя область не получает доступ к
 * таблицам команды и не может завести второго хозяина у состава — ровно та
 * развязка, ради которой состав уехал из двух мест в одно.
 */
public interface RankedTeamPort {

    /** Команда игрока в любом состоянии; пусто — команды нет. */
    Optional<TeamSummary> teamOf(String uid);

    /** Карточки многих команд разом: сотня строк рейтинга — один запрос. */
    Map<String, TeamSummary> teams(Collection<String> teamIds);

    /** Логотипы названных команд; в карте только те, у кого он есть. */
    Map<String, String> logos(Collection<String> teamIds);

    /** Команда в объёме, который нужен соседям: без логотипа и без статистики. */
    record TeamSummary(String teamId, String name, String divisionLanguage, String status,
                       String captainUid, List<String> memberUids) {
    }
}
