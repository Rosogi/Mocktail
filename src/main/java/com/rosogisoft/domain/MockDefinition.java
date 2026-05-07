package com.rosogisoft.domain;

import com.rosogisoft.converter.MapToJsonConverter;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@Entity
@Table(name = "mock_definitions")
@Getter @Setter @NoArgsConstructor
public class MockDefinition {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id", nullable = false)
    private User owner;

    /** Human-readable name, e.g. "Get all users 200 OK" */
    @Column(nullable = false)
    private String name;

    /**
     * HTTP method to match: GET, POST, PUT, DELETE, PATCH, HEAD, OPTIONS
     * Use "*" to match any method.
     */
    @Column(name = "http_method", nullable = false)
    private String httpMethod;

    /**
     * Ant-style path pattern, e.g. /api/users, /api/users/**, /api/orders/{id}
     */
    @Column(name = "path_pattern", nullable = false)
    private String pathPattern;

    /**
     * Optional substring to match in the request body.
     * Useful for SOAP action dispatching or partial JSON matching.
     */
    @Column(name = "request_body_contains")
    private String requestBodyContains;

    @Column(name = "request_match_mode", nullable = false)
    private String requestMatchMode = "basic";

    @Column(name = "request_match_groups", columnDefinition = "TEXT")
    private String requestMatchGroups;

    @Column(name = "response_status", nullable = false)
    private int responseStatus = 200;

    @Column(name = "response_body", columnDefinition = "TEXT")
    private String responseBody;

    @Column(name = "response_content_type")
    private String responseContentType = "application/json";

    /**
     * Extra response headers stored as JSON text, e.g.
     * {"X-Custom-Header":"value","Cache-Control":"no-cache"}
     */
    @Convert(converter = MapToJsonConverter.class)
    @Column(name = "response_headers", columnDefinition = "TEXT")
    private Map<String, String> responseHeaders = new HashMap<>();

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    /**
     * When multiple mocks match the same request, the one with the
     * highest priority wins.
     */
    @Column(name = "priority", nullable = false)
    private int priority = 0;

    /** Optional collection this mock belongs to */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "collection_id")
    private MockCollection collection;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }
}
