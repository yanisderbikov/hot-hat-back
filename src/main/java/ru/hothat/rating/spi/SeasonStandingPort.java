package ru.hothat.rating.spi;

/**
 * Что область рейтинга отвечает соседям.
 *
 * <p>Вопросов два, и оба про текущий сезон: как стоит команда и как стоит
 * игрок. Первый задают карточка команды и подбор соперников — он берёт очки
 * весом при поиске равного противника; второй — карточка игрока. Порт, а не
 * общий репозиторий: так соседняя область не получает доступ к таблицам
 * сезона и не начинает писать в них мимо владельца.
 */
public interface SeasonStandingPort {

    /**
     * Строка команды в таблице текущего сезона; пустая — команда ещё не
     * играла, и это нормальное состояние, а не отсутствие данных.
     */
    TeamStanding currentSeason(String teamId, String mode, String divisionLanguage);

    /**
     * Строка игрока в таблице текущего сезона; пустая — он ещё не играл.
     *
     * <p>Ни ника, ни аватара здесь нет: это копии карточки игрока, и именно
     * они устаревали дольше всего — строка обновлялась только при зачёте
     * следующей партии.
     */
    PlayerStanding currentSeasonPlayer(String uid, String mode, String divisionLanguage);

    /** Показатели команды за сезон в объёме, который показывают экраны. */
    record TeamStanding(int points, int games, int wins, int technicalForfeits) {

        public static final TeamStanding EMPTY = new TeamStanding(0, 0, 0, 0);
    }

    /** Личные показатели за сезон и команда, за которую они набраны. */
    record PlayerStanding(int points, int games, int wins, String teamId) {

        public static final PlayerStanding EMPTY = new PlayerStanding(0, 0, 0, null);
    }
}
