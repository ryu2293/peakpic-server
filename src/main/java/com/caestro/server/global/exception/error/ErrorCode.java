package com.caestro.server.global.exception.error;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    INVALID_TOKEN(401, "유효하지 않은 토큰입니다"),
    TOKEN_EXPIRED(401, "토큰이 만료되었습니다"),
    UNAUTHORIZED(401, "인증이 필요합니다"),
    INVALID_OAUTH_PROVIDER(400, "지원하지 않는 OAuth provider입니다"),
    OAUTH_LOGIN_FAILED(502, "외부 인증 서버 요청에 실패했습니다"),
    MISSING_AUTH_CODE(400, "인가코드가 없습니다"),
    INVALID_REFRESH_TOKEN(401, "유효하지 않은 리프레시 토큰입니다"),
    USER_NOT_FOUND(404, "유저를 찾을 수 없습니다"),
    SESSION_NOT_FOUND(404, "세션을 찾을 수 없습니다"),
    SESSION_ALREADY_CONNECTED(409, "이미 다른 촬영자가 연결된 세션입니다"),
    INVALID_SIGNALING_MESSAGE(400, "유효하지 않은 시그널링 메시지입니다"),
    INTERNAL_SERVER_ERROR(500, "서버 오류가 발생했습니다");

    private final int status;
    private final String message;

}
