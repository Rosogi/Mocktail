package com.rosogisoft.security;

import com.rosogisoft.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "mocktail.deployment", name = "mode", havingValue = "database")
public class PasswordChangeInterceptor implements HandlerInterceptor {

    private final UserService userService;

    @Override
    public boolean preHandle(HttpServletRequest request,
                             HttpServletResponse response,
                             Object handler) throws Exception {
        String path = request.getRequestURI();
        if (isAllowedPath(path)) {
            return true;
        }

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth instanceof AnonymousAuthenticationToken) {
            return true;
        }

        if (userService.isPasswordChangeRequired(auth.getName())) {
            response.sendRedirect("/password/change");
            return false;
        }
        return true;
    }

    private boolean isAllowedPath(String path) {
        return path.equals("/password/change") ||
                path.equals("/login") ||
                path.equals("/logout") ||
                path.startsWith("/webjars/") ||
                path.startsWith("/css/") ||
                path.startsWith("/js/") ||
                path.equals("/favicon.ico");
    }
}
