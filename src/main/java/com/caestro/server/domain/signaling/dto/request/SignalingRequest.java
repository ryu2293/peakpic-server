package com.caestro.server.domain.signaling.dto.request;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record SignalingRequest(
        String type,
        String sessionId,
        String sender,
        String sdpType,
        String sdp,
        String sdpMid,
        Integer sdpMLineIndex,
        String candidate
) {
}
