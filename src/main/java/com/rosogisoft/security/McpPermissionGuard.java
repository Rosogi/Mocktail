package com.rosogisoft.security;

import com.rosogisoft.domain.LlmAccessLevel;
import com.rosogisoft.domain.LlmAccessToken;
import org.springframework.security.access.AccessDeniedException;

public final class McpPermissionGuard {

    private McpPermissionGuard() {
    }

    public static void requireRead(LlmAccessLevel level, String area) {
        if (level == null || !level.canRead()) {
            throw new AccessDeniedException("LLM token does not have read access to " + area + ".");
        }
    }

    public static void requireWrite(LlmAccessLevel level, String area) {
        if (level == null || !level.canWrite()) {
            throw new AccessDeniedException("LLM token does not have write access to " + area + ".");
        }
    }

    public static LlmAccessToken currentToken() {
        var authentication = org.springframework.security.core.context.SecurityContextHolder
                .getContext()
                .getAuthentication();
        if (authentication instanceof McpAuthentication mcpAuthentication) {
            return mcpAuthentication.getAccessToken();
        }
        throw new AccessDeniedException("MCP authentication is required.");
    }
}
