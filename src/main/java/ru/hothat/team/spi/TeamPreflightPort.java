package ru.hothat.team.spi;

import ru.hothat.config.HotHatUser;

/**
 * Что область команды отвечает подбору соперников.
 *
 * <p>Порт отдельный от {@link RankedTeamPort}, потому что здесь есть
 * предусловия: «пара готова» проверяется вместе с членством и дивизионом, а
 * значит зависит от карточки игрока. Проекции команды такой зависимости не
 * имеют, и держать их вместе значило бы навязать её всем, кто спрашивает
 * всего лишь имя команды.
 */
public interface TeamPreflightPort {

    /**
     * Пара, готовая выйти в рейтинговую партию названного режима.
     *
     * <p>Бросает 409 {@code TEAM_PREFLIGHT_NOT_READY}, если проверки нет, она
     * истекла, режим другой или кто-то из двоих ещё не подтвердил связь и
     * готовность. Правило одно и живёт здесь: у подбора своей копии быть не
     * должно.
     */
    LaunchablePreflight requireLaunchablePreflight(HotHatUser user, String gameMode);

    /**
     * Сообщить проверке, что нашёл подбор.
     *
     * <p>Пишет сосед, но через порт: таблица остаётся за владельцем, и
     * «поиск начался» не может разъехаться с тем, что видит напарник.
     */
    void reportSearch(String teamId, String targetRoomId, boolean roomReady,
                      boolean searchStarted, Integer searchCount, boolean failed);

    /** Пара и её замысел: куда она собралась и кто это затеял. */
    record LaunchablePreflight(RankedTeamPort.TeamSummary team, String initiatorUid, String intent,
                               String requestedRoomId, String gameMode) {
    }
}
