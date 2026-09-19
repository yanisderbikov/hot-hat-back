package ru.hothat.lobby.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import ru.hothat.team.api.dto.GameMode;

/**
 * Заявка на подбор так, как её видит подавший.
 *
 * <p>Общая проекция: включается полем и в ответ на подачу заявки, и в ответ на
 * её чтение, и в рейтинговую заявку — форма описана один раз.
 *
 * <p>Поля {@code created} из старого ответа здесь нет намеренно. Оно значило
 * «этим вызовом комната создана» и было верно ровно один раз — при первом из
 * сорока опросов; в чистом чтении ему вообще нет смысла. Тот же вопрос
 * отвечает {@code hostUid}: сравните со своим uid и получите то же самое,
 * причём в любом ответе, а не только в первом.
 */
@Schema(description = "Заявка на подбор комнаты")
public record MatchTicketView(

        /**
         * Сегодня это идентификатор комнаты, в которой заявка стоит: отдельной
         * таблицы заявок в переходном движке нет, и придумывать второй
         * идентификатор поверх того же ряда значило бы завести два имени
         * одному предмету. Клиенту это знать не нужно — он адресует заявку
         * этим значением и не разбирает его.
         */
        @Schema(description = "Идентификатор заявки: им адресуются чтение и отмена",
                example = "hat-0f3a9c1d7b2e5480")
        String ticketId,

        @Schema(description = "Что сейчас с заявкой", example = "searching")
        MatchTicketState state,

        @Schema(description = "Комната, в которую заявка привела", example = "hat-0f3a9c1d7b2e5480",
                pattern = "^hat-[a-f0-9]{16}$")
        String roomId,

        @Schema(description = "Хозяин комнаты: совпал с вашим uid — комнату завели вы",
                example = "Qk3xZaTb9mNpR2sVuWyA1cEfGhJk", nullable = true)
        String hostUid,

        @Schema(description = "Сколько игроков уже собралось", example = "4", type = "integer")
        int playersJoined,

        @Schema(description = "Сколько нужно собрать", example = "10", type = "integer")
        int maxPlayers,

        @Schema(description = "До какого момента идёт поиск, миллисекунды эпохи; 0 — срока нет",
                example = "1788600120000", type = "integer")
        long deadlineAtMs,

        @Schema(description = "Режим партии, под который идёт подбор", example = "classic")
        GameMode gameMode,

        /**
         * null бывает у единственного исхода — заявка не дожила до комнаты
         * ({@code state=expired}). Подбор в этом случае не называет язык, а
         * выдумывать его за него нельзя: игроку он всё равно не пригодится,
         * а неправда в отчёте останется.
         */
        @Schema(description = "Язык слов в подбираемой партии; null — заявка истекла и языка у неё нет",
                example = "ru", nullable = true,
                allowableValues = {"ru", "en", "de", "es", "fr", "it", "zh", "ja", "kk"})
        String gameLanguage) {
}
