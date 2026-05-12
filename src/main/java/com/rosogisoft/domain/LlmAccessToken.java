package com.rosogisoft.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "llm_access_tokens")
@Getter
@Setter
@NoArgsConstructor
public class LlmAccessToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id", nullable = false)
    private User owner;

    @Column(name = "token_hash", nullable = false, unique = true, length = 128)
    private String tokenHash;

    @Column(name = "token_preview", nullable = false, length = 64)
    private String tokenPreview;

    @Enumerated(EnumType.STRING)
    @Column(name = "request_logs_access", nullable = false, length = 20)
    private LlmAccessLevel requestLogsAccess = LlmAccessLevel.NONE;

    @Enumerated(EnumType.STRING)
    @Column(name = "mocks_access", nullable = false, length = 20)
    private LlmAccessLevel mocksAccess = LlmAccessLevel.NONE;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "regenerated_at")
    private Instant regeneratedAt;

    @Column(name = "last_used_at")
    private Instant lastUsedAt;
}
