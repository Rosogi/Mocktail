package com.rosogisoft.security;

import com.rosogisoft.domain.LlmAccessToken;
import com.rosogisoft.domain.User;
import org.springframework.security.authentication.AbstractAuthenticationToken;

import java.util.List;

public class McpAuthentication extends AbstractAuthenticationToken {

    private final LlmAccessToken accessToken;

    public McpAuthentication(LlmAccessToken accessToken) {
        super(List.of());
        this.accessToken = accessToken;
        setAuthenticated(true);
    }

    @Override
    public Object getCredentials() {
        return accessToken.getTokenPreview();
    }

    @Override
    public User getPrincipal() {
        return accessToken.getOwner();
    }

    @Override
    public String getName() {
        User owner = accessToken.getOwner();
        return owner != null ? owner.getUsername() : "mcp";
    }

    public LlmAccessToken getAccessToken() {
        return accessToken;
    }
}
