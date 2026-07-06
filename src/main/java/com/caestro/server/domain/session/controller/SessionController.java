package com.caestro.server.domain.session.controller;

import com.caestro.server.domain.session.controller.api.SessionApi;
import com.caestro.server.domain.session.dto.response.SessionResponse;
import com.caestro.server.domain.session.entity.Session;
import com.caestro.server.domain.session.service.SessionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/sessions")
@RequiredArgsConstructor
public class SessionController implements SessionApi {

    private final SessionService sessionService;

    @GetMapping("/{sessionId}")
    @Override
    public ResponseEntity<SessionResponse> getSession(@PathVariable Long sessionId) {
        Session session = sessionService.getSession(sessionId);
        return ResponseEntity.ok(SessionResponse.from(session));
    }
}
