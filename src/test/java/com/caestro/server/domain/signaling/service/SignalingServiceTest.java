package com.caestro.server.domain.signaling.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.caestro.server.domain.devicespec.service.DeviceSpecService;
import com.caestro.server.domain.session.service.SessionService;
import com.caestro.server.domain.signaling.entity.SessionInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.web.socket.WebSocketSession;

/**
 * handleDisconnect의 소켓-슬롯 일치 검증 테스트.
 *
 * 배경: 재연결(takeover) 시 옛 소켓의 Redis 매핑을 지워 유령 disconnect를 막지만(1차 방어),
 * 다중 인스턴스에서는 "매핑 조회 ~ 삭제" 사이 레이스 창이 넓어진다.
 * 이 창에서 유령 소켓의 disconnect가 처리되면, 어느 슬롯과도 일치하지 않는 소켓임에도
 * 방장 이탈로 오분류되어 갓 복구된 세션의 ownerSocketId를 null로 되돌리고
 * 잘못된 PEER_DISCONNECTED까지 발송하는 결함이 있었다.
 */
@ExtendWith(MockitoExtension.class)
class SignalingServiceTest {

    @Mock
    private RedisTemplate<String, String> redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private WebSocketSessionManager sessionManager;

    @Mock
    private SessionService sessionService;

    @Mock
    private DeviceSpecService deviceSpecService;

    @Mock
    private SignalingDiagnosticLogger diagnosticLogger;

    @Mock
    private SignalingRelaySender relaySender;

    @Mock
    private WebSocketSession socket;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private SignalingService signalingService;

    private static final String SESSION_CODE = "K7P2QM";
    private static final String OWNER_SOCKET = "owner-sock";
    private static final String PARTICIPANT_SOCKET = "participant-sock";

    @BeforeEach
    void setUp() {
        signalingService = new SignalingService(
                redisTemplate, sessionManager, objectMapper,
                sessionService, deviceSpecService, diagnosticLogger, relaySender);
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
    }

    /**
     * owner=o1/participant=p1으로 모두 차 있는 정상 세션을 Redis 모킹에 심는다.
     */
    private void givenConnectedSession(String disconnectingSocketId) throws Exception {
        SessionInfo info = new SessionInfo();
        info.setSessionCode(SESSION_CODE);
        info.setOwnerUserId(1L);
        info.setOwnerSocketId(OWNER_SOCKET);
        info.setParticipantUserId(2L);
        info.setParticipantSocketId(PARTICIPANT_SOCKET);
        info.setCurrentDirectorUserId(1L);
        info.setStatus("CONNECTED");

        given(socket.getId()).willReturn(disconnectingSocketId);
        given(valueOperations.get("socket:" + disconnectingSocketId)).willReturn(SESSION_CODE);
        given(valueOperations.get("session:" + SESSION_CODE))
                .willReturn(objectMapper.writeValueAsString(info));
    }

    @Test
    @DisplayName("유령 소켓(어느 슬롯과도 불일치)의 disconnect는 세션을 훼손하지 않고 매핑만 정리한다")
    void handleDisconnect_ghostSocket_doesNotTouchSession() throws Exception {
        // 유령: takeover로 이미 슬롯에서 교체된 옛 소켓 — owner/participant 어느 쪽 socketId와도 다르다
        givenConnectedSession("ghost-sock");

        signalingService.handleDisconnect(socket);

        // 1. 세션 상태를 저장(변경)하면 안 된다 — 방장 이탈로 오분류되어 ownerSocketId=null 되는 결함 방지
        verify(valueOperations, never()).set(startsWith("session:"), any(), anyLong(), any());
        // 2. 잘못된 PEER_DISCONNECTED를 보내면 안 된다
        verify(relaySender, never()).send(any(), any());
        // 3. 유령 소켓의 매핑은 정리한다
        verify(redisTemplate).delete("socket:ghost-sock");
    }

    @Test
    @DisplayName("[회귀] 방장 소켓 disconnect는 유예 처리(ownerSocketId=null, WAITING)하고 상대에게 알린다")
    void handleDisconnect_ownerSocket_gracePeriod() throws Exception {
        givenConnectedSession(OWNER_SOCKET);

        signalingService.handleDisconnect(socket);

        verify(relaySender).send(eq(PARTICIPANT_SOCKET), any());

        ArgumentCaptor<String> savedJson = ArgumentCaptor.forClass(String.class);
        verify(valueOperations).set(eq("session:" + SESSION_CODE), savedJson.capture(),
                anyLong(), any(TimeUnit.class));
        SessionInfo saved = objectMapper.readValue(savedJson.getValue(), SessionInfo.class);
        assertThat(saved.getOwnerSocketId()).isNull();
        assertThat(saved.getOwnerUserId()).isEqualTo(1L); // takeover 가능하도록 정체성은 유지
        assertThat(saved.getStatus()).isEqualTo("WAITING");

        verify(redisTemplate).delete("socket:" + OWNER_SOCKET);
    }

    @Test
    @DisplayName("[회귀] 참여자 소켓 disconnect는 슬롯을 비우고(WAITING) 디렉터를 방장으로 되돌린다")
    void handleDisconnect_participantSocket_clearsSlot() throws Exception {
        givenConnectedSession(PARTICIPANT_SOCKET);

        signalingService.handleDisconnect(socket);

        verify(relaySender).send(eq(OWNER_SOCKET), any());

        ArgumentCaptor<String> savedJson = ArgumentCaptor.forClass(String.class);
        verify(valueOperations).set(eq("session:" + SESSION_CODE), savedJson.capture(),
                anyLong(), any(TimeUnit.class));
        SessionInfo saved = objectMapper.readValue(savedJson.getValue(), SessionInfo.class);
        assertThat(saved.getParticipantUserId()).isNull();
        assertThat(saved.getParticipantSocketId()).isNull();
        assertThat(saved.getCurrentDirectorUserId()).isEqualTo(1L);
        assertThat(saved.getStatus()).isEqualTo("WAITING");

        verify(redisTemplate).delete("socket:" + PARTICIPANT_SOCKET);
    }
}
