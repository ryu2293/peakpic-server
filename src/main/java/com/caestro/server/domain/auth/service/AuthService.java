package com.caestro.server.domain.auth.service;

import com.caestro.server.domain.auth.dto.response.TokenResponse;
import com.caestro.server.domain.auth.oauth.OAuthProvider;
import com.caestro.server.domain.auth.oauth.OAuthProvider.OAuthProfile;
import com.caestro.server.domain.auth.oauth.OAuthProviderRegistry;
import com.caestro.server.domain.user.entity.User;
import com.caestro.server.domain.user.repository.UserRepository;
import com.caestro.server.global.exception.CustomException;
import com.caestro.server.global.exception.error.ErrorCode;
import com.caestro.server.global.jwt.JwtProvider;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthService {

    private static final String REFRESH_KEY_PREFIX = "refresh:";

    private final UserRepository userRepository;
    private final JwtProvider jwtProvider;
    private final RedisTemplate<String, String> redisTemplate;
    private final OAuthProviderRegistry oAuthProviderRegistry;

    @Value("${jwt.refresh-expiration}")
    private long refreshExpiration;

    public TokenResponse socialLogin(String providerName, String code) {
        OAuthProvider provider = oAuthProviderRegistry.getProvider(providerName);
        OAuthProfile profile = provider.getProfile(code);
        User user = findOrCreateUser(profile, providerName);
        return generateTokens(user);
    }

    private User findOrCreateUser(OAuthProfile profile, String provider) {
        return userRepository.findByOauthProviderAndOauthId(provider, profile.oauthId())
                .orElseGet(() -> userRepository.save(User.builder()
                        .oauthProvider(provider)
                        .oauthId(profile.oauthId())
                        .nickname(profile.nickname())
                        .profileImage(profile.profileImage())
                        .role(User.Role.USER)
                        .build()));
    }

    private TokenResponse generateTokens(User user) {
        String accessToken = jwtProvider.generateAccessToken(user.getId(), user.getRole());
        String refreshToken = jwtProvider.generateRefreshToken(user.getId());

        redisTemplate.opsForValue().set(
                REFRESH_KEY_PREFIX + user.getId(),
                refreshToken,
                Duration.ofSeconds(refreshExpiration)
        );

        return new TokenResponse(accessToken, refreshToken);
    }

    public TokenResponse refreshAccessToken(String refreshToken) {
        if (!jwtProvider.validateRefreshToken(refreshToken)) {
            throw new CustomException(ErrorCode.INVALID_REFRESH_TOKEN);
        }

        Long userId = jwtProvider.getUserIdFromRefreshToken(refreshToken);
        String storedToken = redisTemplate.opsForValue().get(REFRESH_KEY_PREFIX + userId);

        if (storedToken == null || !storedToken.equals(refreshToken)) {
            redisTemplate.delete(REFRESH_KEY_PREFIX + userId);
            throw new CustomException(ErrorCode.INVALID_REFRESH_TOKEN);
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        return generateTokens(user);
    }

    public void logout(Long userId) {
        redisTemplate.delete(REFRESH_KEY_PREFIX + userId);
    }
}
