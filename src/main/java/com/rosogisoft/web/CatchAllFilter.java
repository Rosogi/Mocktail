package com.rosogisoft.web;

import com.rosogisoft.domain.MockDefinition;
import com.rosogisoft.domain.RequestLog;
import com.rosogisoft.domain.SettingKey;
import com.rosogisoft.domain.User;
import com.rosogisoft.config.AppProperties;
import com.rosogisoft.config.DeploymentMode;
import com.rosogisoft.config.MocktailProperties;
import com.rosogisoft.service.EnvironmentService;
import com.rosogisoft.service.MockMatcherService;
import com.rosogisoft.service.MockTemplateEngine;
import com.rosogisoft.service.RequestLogService;
import com.rosogisoft.service.TemplatePhase;
import com.rosogisoft.service.TemplateRenderContext;
import com.rosogisoft.service.UserService;
import com.rosogisoft.service.UserSettingsService;
import com.rosogisoft.ws.RequestEventPublisher;
import jakarta.servlet.*;
import jakarta.servlet.annotation.WebFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;

@Slf4j
@Component
@Order(1)
@WebFilter("/*")
@RequiredArgsConstructor
public class CatchAllFilter implements Filter {

    private final UserService userService;
    private final MockMatcherService mockMatcher;
    private final RequestLogService logService;
    private final RequestEventPublisher publisher;
    private final MockTemplateEngine templateEngine;
    private final UserSettingsService settingsService;
    private final EnvironmentService environmentService;
    private final AppProperties appProperties;
    private final MocktailProperties mocktailProperties;

    @Override
    public void doFilter (ServletRequest req,
                          ServletResponse res,
                          FilterChain chain) throws IOException, ServletException {

        HttpServletRequest request = (HttpServletRequest) req;
        HttpServletResponse response = (HttpServletResponse) res;

        int port = request.getLocalPort();

        if (!isMockPort(port)) {
            chain.doFilter(req, res);
            return;
        }

        String method = request.getMethod();
        String path = request.getRequestURI();
        String queryString = request.getQueryString();
        String body = readBody(request);
        String contentType = request.getContentType();
        String remoteAddr = request.getRemoteAddr();
        Map<String, String> headers = extractHeaders(request);

        // Find owner by port
        Optional<User> ownerOpt = userService.findByPort(port);
        if (ownerOpt.isEmpty()) {
            writeResponse(response, 404, "application/json", "{\"error\":\"No user assigned to this port\"}");
            return;
        }

        User owner = ownerOpt.get();
        var environmentContext = environmentService.contextForRequest(owner, queryString, headers);

        // Match against owner's active mocks
        Optional<MockDefinition> mockOpt =
                mockMatcher.findMatch(owner.getId(), method, path, queryString, headers, body,
                        environmentContext);

        RequestLog logEntry = new RequestLog();
        logEntry.setUserPort(port);
        logEntry.setOwner(owner);
        logEntry.setMethod(method);
        logEntry.setPath(path);
        logEntry.setQueryParams(queryString);
        logEntry.setRequestHeaders(headers);
        logEntry.setRequestBody(body);
        logEntry.setContentType(contentType);
        logEntry.setRemoteAddr(remoteAddr);

        if (mockOpt.isPresent()) {
            MockDefinition mock = mockOpt.get();
            TemplateRenderContext templateContext = new TemplateRenderContext(
                    owner.getId(), method, path, queryString, headers, body, environmentContext, TemplatePhase.RESPONSE);

            // Set response headers from mock
            if (mock.getResponseHeaders() != null) {
                mock.getResponseHeaders().forEach((name, value) -> {
                    String resolvedName = templateEngine.render(name, templateContext);
                    String resolvedValue = templateEngine.render(value, templateContext);
                    response.setHeader(resolvedName, resolvedValue);
                });
            }

            String responseContentType = templateEngine.render(mock.getResponseContentType(), templateContext);

            String responseBody = templateEngine.render(
                    mock.getResponseBody() != null ? mock.getResponseBody() : "",
                    templateContext
            );
            writeResponse(response, mock.getResponseStatus(), responseContentType, responseBody);

            logEntry.setMatchedMock(mock);
            logEntry.setResponseStatus(mock.getResponseStatus());
            logEntry.setResponseBody(responseBody);

        } else {
            var settings = settingsService.getSettings(owner);
            TemplateRenderContext templateContext = new TemplateRenderContext(
                    owner.getId(), method, path, queryString, headers, body, environmentContext, TemplatePhase.RESPONSE);
            String responseBody = templateEngine.render(
                    settings.get(SettingKey.DEFAULT_RESPONSE_BODY),
                    templateContext
            );
            int status = settings.getInt(SettingKey.DEFAULT_RESPONSE_STATUS);

            String responseContentType = templateEngine.render(settings.get(SettingKey.DEFAULT_RESPONSE_CT), templateContext);
            writeResponse(response, status, responseContentType, responseBody);

            logEntry.setResponseStatus(status);
            logEntry.setResponseBody(responseBody);
        }

        // Persist and broadcast
        RequestLog saved = logService.save(logEntry);
        publisher.publish(owner.getId(), saved);
    }

    // ---------------------------------------------------------------
    private boolean isMockPort(int port) {
        if (mocktailProperties.mode() == DeploymentMode.STANDALONE) {
            return port == mocktailProperties.getStandalone().getUserPort();
        }
        return port >= appProperties.getRangeStart() && port <= appProperties.getRangeEnd();
    }

    private void writeResponse(HttpServletResponse response,
                               int status,
                               String contentType,
                               String body) throws IOException {
        byte[] bytes = (body != null ? body : "").getBytes(StandardCharsets.UTF_8);
        response.setStatus(status);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(utf8ContentType(contentType));
        response.setContentLength(bytes.length);
        response.getOutputStream().write(bytes);
    }

    private String utf8ContentType(String contentType) {
        String value = contentType != null && !contentType.isBlank()
                ? contentType.trim()
                : "text/plain";
        if (hasCharset(value) || !isTextContentType(value)) {
            return value;
        }
        return value + ";charset=UTF-8";
    }

    private boolean hasCharset(String contentType) {
        return Arrays.stream(contentType.split(";"))
                .skip(1)
                .map(String::trim)
                .anyMatch(part -> part.toLowerCase(Locale.ROOT).startsWith("charset="));
    }

    private boolean isTextContentType(String contentType) {
        String mediaType = contentType.split(";", 2)[0].trim().toLowerCase(Locale.ROOT);
        return mediaType.startsWith("text/") ||
                mediaType.equals("application/json") ||
                mediaType.endsWith("+json") ||
                mediaType.equals("application/xml") ||
                mediaType.endsWith("+xml") ||
                mediaType.equals("application/javascript") ||
                mediaType.equals("application/x-www-form-urlencoded");
    }

    private String readBody (HttpServletRequest request) {
        try {
            byte[] bytes = StreamUtils.copyToByteArray(request.getInputStream());
            return bytes.length == 0 ? null : new String(bytes, StandardCharsets.UTF_8);
        } catch (IOException e) {
            return null;
        }
    }

    private Map<String, String> extractHeaders (HttpServletRequest request) {
        Map<String, String> map = new LinkedHashMap<>();
        Enumeration<String> names = request.getHeaderNames();
        while (names != null && names.hasMoreElements()) {
            String name = names.nextElement();
            String value = request.getHeader(name);
            // Skip internal / low-value headers to keep storage lean
            if (!name.equalsIgnoreCase("cookie") &&
                    !name.equalsIgnoreCase("connection")) {
                map.put(name, value);
            }
        }
        return map;
    }
}
