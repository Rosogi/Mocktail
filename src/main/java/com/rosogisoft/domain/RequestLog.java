package com.rosogisoft.domain;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rosogisoft.converter.MapToJsonConverter;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@Entity
@Table(name = "request_logs")
@Getter
@Setter
@NoArgsConstructor
public class RequestLog {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_port", nullable = false)
    private int userPort;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id")
    private User owner;

    @Column(nullable = false)
    private Instant timestamp = Instant.now();

    @Column(nullable = false, length = 10)
    private String method;

    @Column(nullable = false, length = 2000)
    private String path;

    @Column(name = "query_params")
    private String queryParams;

    @Convert(converter = MapToJsonConverter.class)
    @Column(name = "request_headers", columnDefinition = "TEXT")
    private Map<String, String> requestHeaders = new HashMap<>();

    @Column(name = "request_body", columnDefinition = "TEXT")
    private String requestBody;

    @Column(name = "content_type")
    private String contentType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "matched_mock_id")
    private MockDefinition matchedMock;

    @Column(name = "response_status")
    private Integer responseStatus;

    @Column(name = "response_body", columnDefinition = "TEXT")
    private String responseBody;

    @Column(name = "remote_addr", length = 100)
    private String remoteAddr;

    @Transient
    public String getRequestHeadersJson() {
        if (requestHeaders == null || requestHeaders.isEmpty()) {
            return "";
        }
        try {
            return MAPPER.writeValueAsString(requestHeaders);
        } catch (JsonProcessingException e) {
            return requestHeaders.toString();
        }
    }
}
