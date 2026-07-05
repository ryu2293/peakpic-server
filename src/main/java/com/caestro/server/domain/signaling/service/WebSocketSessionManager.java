package com.caestro.server.domain.signaling.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
@RequiredArgsConstructor
public class WebSocketSessionManager {

    private final ObjectMapper objectMapper;
    private final Map<String, WebSocketSession> socketMap = new ConcurrentHashMap<>();

    public void addSession(WebSocketSession session) {
        socketMap.put(session.getId(), session);
        log.info("WebSocket Session Add: {}", session.getId());
    }

    public void removeSession(WebSocketSession session) {
        socketMap.remove(session.getId());
        log.info("WebSocket Session Remove: {}", session.getId());
    }

    public void sendMessage(String socketId, Object payload) {
        if (socketId == null) return;
        
        WebSocketSession target = socketMap.get(socketId);
        if (target != null && target.isOpen()) {
            try {
                target.sendMessage(new TextMessage(objectMapper.writeValueAsString(payload)));
            } catch (IOException e) {
                log.error("Failed to send message to session {}", socketId, e);
            }
        } else {
            log.warn("Target session {} not found or closed", socketId);
        }
    }
}
