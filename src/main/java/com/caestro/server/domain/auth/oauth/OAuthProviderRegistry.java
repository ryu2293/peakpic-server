package com.caestro.server.domain.auth.oauth;

import com.caestro.server.global.exception.CustomException;
import com.caestro.server.global.exception.error.ErrorCode;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class OAuthProviderRegistry {

    private final Map<String, OAuthProvider> providers;

    public OAuthProviderRegistry(List<OAuthProvider> providerList) {
        this.providers = providerList.stream()
                .collect(Collectors.toMap(OAuthProvider::getName, Function.identity()));
    }

    public OAuthProvider getProvider(String name) {
        OAuthProvider provider = providers.get(name);
        if (provider == null) {
            throw new CustomException(ErrorCode.INVALID_OAUTH_PROVIDER);
        }
        return provider;
    }
}
