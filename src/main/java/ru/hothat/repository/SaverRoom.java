package ru.hothat.repository;

import ru.hothat.model.room.Room;
import ru.hothat.model.room.RoomChatMessage;
import ru.hothat.model.room.RoomPlayer;
import ru.hothat.model.room.RoomSpectator;
import ru.hothat.model.room.RoomTeam;
import ru.hothat.model.room.RoomWordSubmission;

import java.util.List;

public interface SaverRoom {

    Room save(Room room);

    RoomPlayer savePlayer(RoomPlayer player);

    List<RoomPlayer> savePlayers(List<RoomPlayer> players);

    void deletePlayer(String roomId, String uid);

    RoomSpectator saveSpectator(RoomSpectator spectator);

    void deleteSpectator(String roomId, String uid);

    RoomTeam saveTeam(RoomTeam team);

    List<RoomTeam> saveTeams(List<RoomTeam> teams);

    void deleteTeam(String roomId, String teamId);

    RoomChatMessage saveChatMessage(RoomChatMessage message);

    RoomWordSubmission saveWordSubmission(RoomWordSubmission submission);

    void deleteWordSubmission(String roomId, String submissionId);

    /** Удаление комнаты вместе со всеми подколлекциями (аналог deleteDocumentTree). */
    void deleteRoomTree(String roomId);
}
