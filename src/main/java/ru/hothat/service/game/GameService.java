package ru.hothat.service.game;

import ru.hothat.config.HotHatUser;

import java.util.Map;

/** Действия партии: подготовка, ходы, диверсии, апелляция. */
public interface GameService {

    Map<String, Object> prepareGame(HotHatUser user, String roomId);

    Map<String, Object> rankedAutostart(HotHatUser user, String roomId);

    Map<String, Object> setupHostActivity(HotHatUser user, String roomId, Map<String, Object> body);

    Map<String, Object> manualHostTransfer(HotHatUser user, String roomId, Map<String, Object> body);

    Map<String, Object> setRecordingPreference(HotHatUser user, String roomId, Map<String, Object> body);

    /** Сторож бездействующего хозяина комнаты в setup. */
    Map<String, Object> setupHostWatch(HotHatUser user, String roomId);

    Map<String, Object> randomizeTeams(HotHatUser user, String roomId);

    Map<String, Object> kickPlayer(HotHatUser user, String roomId, Map<String, Object> body);

    /** Сверка присутствия с LiveKit: ставит партию на паузу и снимает её. */
    Map<String, Object> syncGamePresence(HotHatUser user, String roomId, Map<String, Object> body);

    Map<String, Object> toggleManualPause(HotHatUser user, String roomId, Map<String, Object> body);

    Map<String, Object> replaceMemeSlot(HotHatUser user, String roomId, Map<String, Object> body);

    Map<String, Object> useSabotage(HotHatUser user, String roomId, Map<String, Object> body);

    Map<String, Object> createReplacementRecording(HotHatUser user, String roomId);

    Map<String, Object> replacementRecordingResult(HotHatUser user, String roomId, Map<String, Object> body);

    Map<String, Object> discardReplacementRecording(HotHatUser user, String roomId, Map<String, Object> body);

    Map<String, Object> voteAppeal(HotHatUser user, String roomId, Map<String, Object> body);

    /** Подведение итогов хода после голосования; зовётся и раннером тест-ботов. */
    Map<String, Object> finalizeAppeal(HotHatUser user, String roomId);
}
