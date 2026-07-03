package com.caestro.server.domain.auth.oauth;

public interface OAuthProvider {

    String getName();

    OAuthProfile getProfile(String code);

    record OAuthProfile(String oauthId, String nickname, String profileImage) {
    }
}
