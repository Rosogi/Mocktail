package com.rosogisoft.web;

import com.rosogisoft.domain.MockDefinition;
import com.rosogisoft.domain.RequestLog;
import com.rosogisoft.domain.User;
import com.rosogisoft.service.MockMatcherService;
import com.rosogisoft.service.RequestLogService;
import com.rosogisoft.service.UserService;
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

    @Override
    public void doFilter (ServletRequest req,
                          ServletResponse res,
                          FilterChain chain) throws IOException, ServletException {

        HttpServletRequest request = (HttpServletRequest) req;
        HttpServletResponse response = (HttpServletResponse) res;

        int port = request.getLocalPort();

        // Only intercept user mock ports (9000-9999)
        if (port < 9000 || port > 9999) {
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
            response.setStatus(404);
            response.setContentType("application/json");
            response.getWriter().write("{\"error\":\"No user assigned to this port\"}");
            return;
        }

        User owner = ownerOpt.get();

        // Match against owner's active mocks
        Optional<MockDefinition> mockOpt =
                mockMatcher.findMatch(owner.getId(), method, path, body);

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

            // Set response headers from mock
            if (mock.getResponseHeaders() != null) {
                mock.getResponseHeaders().forEach(response::setHeader);
            }

            response.setStatus(mock.getResponseStatus());
            response.setContentType(mock.getResponseContentType());

            String responseBody = mock.getResponseBody() != null ? mock.getResponseBody() : "";
            response.getWriter().write(responseBody);

            logEntry.setMatchedMock(mock);
            logEntry.setResponseStatus(mock.getResponseStatus());
            logEntry.setResponseBody(responseBody);

        } else {
            // No mock found
            response.setStatus(404);
            response.setContentType("application/json");
            String notFound = """
                    {"error":"No mock matched","method":"%s","path":"%s"}
                    """.formatted(method, path).trim();
            response.getWriter().write(notFound);

            logEntry.setResponseStatus(404);
            logEntry.setResponseBody(notFound);
        }

        // Persist and broadcast
        RequestLog saved = logService.save(logEntry);
        publisher.publish(owner.getUsername(), saved);
    }

    // ---------------------------------------------------------------
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
