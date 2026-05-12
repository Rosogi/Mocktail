package com.rosogisoft.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rosogisoft.config.MocktailProperties;
import com.rosogisoft.service.LlmAccessTokenService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class McpTokenAuthenticationFilter extends OncePerRequestFilter {

    private final MocktailProperties mocktailProperties;
    private final LlmAccessTokenService tokenService;
    private final ObjectMapper objectMapper;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getServletPath();
        return !("/mcp".equals(path) || path.startsWith("/mcp/"));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if (!mocktailProperties.getMcp().isEnabled()) {
            writeJson(response, HttpServletResponse.SC_NOT_FOUND, Map.of("error", "MCP is disabled."));
            return;
        }

        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            response.setStatus(HttpServletResponse.SC_NO_CONTENT);
            return;
        }

        String authHeader = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            writeJson(response, HttpServletResponse.SC_UNAUTHORIZED,
                    Map.of("error", "Bearer token is required."));
            return;
        }

        String presentedToken = authHeader.substring("Bearer ".length()).trim();
        var accessToken = tokenService.authenticate(presentedToken);
        if (accessToken.isEmpty()) {
            writeJson(response, HttpServletResponse.SC_UNAUTHORIZED,
                    Map.of("error", "Invalid LLM token."));
            return;
        }

        try {
            SecurityContextHolder.getContext()
                    .setAuthentication(new McpAuthentication(accessToken.get()));
            filterChain.doFilter(request, response);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private void writeJson(HttpServletResponse response, int status, Map<String, String> body) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getWriter(), body);
    }
}
