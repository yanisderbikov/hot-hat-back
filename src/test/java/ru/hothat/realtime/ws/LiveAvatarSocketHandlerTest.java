package ru.hothat.realtime.ws;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import ru.hothat.config.HotHatUser;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** SCRUM-46: ephemeral alpha frames require auth and cannot impersonate UID. */
class LiveAvatarSocketHandlerTest {
    private final LiveAvatarSocketHandler handler=new LiveAvatarSocketHandler(new ObjectMapper());

    private WebSocketSession connect(String sessionId,String uid,boolean guest) throws Exception {
        WebSocketSession socket=mock(WebSocketSession.class);
        HotHatUser user=mock(HotHatUser.class);
        when(user.uid()).thenReturn(uid);
        when(user.guest()).thenReturn(guest);
        when(socket.getId()).thenReturn(sessionId);
        when(socket.isOpen()).thenReturn(true);
        when(socket.getAttributes()).thenReturn(new HashMap<>(
                Map.of(ChannelHandshakeAuthenticator.USER_ATTRIBUTE,user)));
        handler.afterConnectionEstablished(socket);
        return socket;
    }
    private static String alpha(){
        return "data:image/webp;base64,"+"A".repeat(80);
    }

    @Test
    void subscribedUserSeesSourceIdentifiedByHandshakeNotUntrustedFrameUid() throws Exception {
        var sender=connect("src","legitimate-uid",false);
        var viewer=connect("viewer","viewer-uid",false);
        handler.handleTextMessage(viewer,new TextMessage(
                "{\"type\":\"watch\",\"uids\":[\"legitimate-uid\"]}"));
        handler.handleTextMessage(sender,new TextMessage(
                "{\"type\":\"frame\",\"uid\":\"forged-victim\",\"data\":\""+alpha()+"\"}"));
        var capture=org.mockito.ArgumentCaptor.forClass(TextMessage.class);
        verify(viewer,atLeast(2)).sendMessage(capture.capture());
        assertThat(capture.getAllValues().stream()
                .anyMatch(msg->msg.getPayload().contains("\"uid\":\"legitimate-uid\"")
                        && msg.getPayload().contains("data:image/webp;base64,"))).isTrue();
        assertThat(capture.getAllValues().stream()
                .noneMatch(msg->msg.getPayload().contains("forged-victim"))).isTrue();
    }

    @Test
    void unmatchedViewerReceivesNoFrames() throws Exception {
        var sender=connect("src","sender-uid",false);
        var viewer=connect("viewer","other-uid",false);
        handler.handleTextMessage(viewer,new TextMessage(
                "{\"type\":\"watch\",\"uids\":[\"somebody-else\"]}"));
        handler.handleTextMessage(sender,new TextMessage(
                "{\"type\":\"frame\",\"data\":\""+alpha()+"\"}"));
        verify(viewer,times(1)).sendMessage(any(TextMessage.class)); // only hello
    }

    @Test
    void onlyAlphaImagesAreForwardedAndAbsenceRestoresAvatar() throws Exception {
        var sender=connect("src","sender-uid",false);
        var viewer=connect("viewer","viewer-uid",false);
        handler.handleTextMessage(viewer,new TextMessage(
                "{\"type\":\"watch\",\"uids\":[\"sender-uid\"]}"));
        handler.handleTextMessage(sender,new TextMessage(
                "{\"type\":\"frame\",\"data\":\"data:image/jpeg;base64,"+"A".repeat(80)+"\"}"));
        verify(viewer,times(1)).sendMessage(any(TextMessage.class));
        handler.handleTextMessage(sender,new TextMessage(
                "{\"type\":\"frame\",\"data\":\""+alpha()+"\"}"));
        handler.handleTextMessage(sender,new TextMessage("{\"type\":\"off\"}"));
        var capture=org.mockito.ArgumentCaptor.forClass(TextMessage.class);
        verify(viewer,atLeast(3)).sendMessage(capture.capture());
        assertThat(capture.getAllValues().stream()
                .anyMatch(msg->msg.getPayload().contains("\"type\":\"off\""))).isTrue();
    }

    @Test
    void guestCannotOpenUniversalAvatarStream() throws Exception {
        var guest=connect("visitor","guest-id",true);
        verify(guest).close(CloseStatus.POLICY_VIOLATION);
        verify(guest,never()).sendMessage(any(TextMessage.class));
    }

    @Test
    void disconnectClearsPreviousAvatarAndNotifiesOtherViewers() throws Exception {
        var owner=connect("src","sender-uid",false);
        var watcher=connect("viewer","viewer-uid",false);
        handler.handleTextMessage(watcher,new TextMessage(
                "{\"type\":\"watch\",\"uids\":[\"sender-uid\"]}"));
        handler.handleTextMessage(owner,new TextMessage(
                "{\"type\":\"frame\",\"data\":\""+alpha()+"\"}"));
        handler.afterConnectionClosed(owner,CloseStatus.NORMAL);
        var capture=org.mockito.ArgumentCaptor.forClass(TextMessage.class);
        verify(watcher,atLeast(3)).sendMessage(capture.capture());
        assertThat(capture.getAllValues().stream()
                .anyMatch(msg->msg.getPayload().contains("\"type\":\"off\""))).isTrue();
    }
}
