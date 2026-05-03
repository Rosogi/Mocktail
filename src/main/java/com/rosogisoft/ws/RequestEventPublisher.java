package com.rosogisoft.ws;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.rosogisoft.domain.RequestLog;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

@Component
@RequiredArgsConstructor
public class RequestEventPublisher {

    private final SimpMessagingTemplate template;

    private static final DateTimeFormatter FMT =
            DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneOffset.UTC);

    /**
     * Sends a lightweight event to /topic/logs/user-{userId}.
     * The frontend subscribes to this topic and appends rows in real time.
     */
    public void publish (Long userId, RequestLog log) {
        var payload = new LogEvent(
                log.getId(),
                FMT.format(log.getTimestamp()),
                log.getMethod(),
                log.getPath(),
                log.getQueryParams(),
                log.getResponseStatus(),
                log.getMatchedMock() != null ? log.getMatchedMock().getName() : null,
                log.getRequestHeadersJson(),
                log.getRequestBody(),
                log.getResponseBody(),
                log.getContentType(),
                log.getRemoteAddr()
        );
        template.convertAndSend("/topic/logs/user-" + userId, payload);
    }

    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
    public record LogEvent(
            Long id,
            String time,
            String method,
            String path,
            String queryParams,
            Integer status,
            String matchedMock,
            String requestHeaders,
            String requestBody,
            String responseBody,
            String contentType,
            String remoteAddr
    ) {
    }
}
