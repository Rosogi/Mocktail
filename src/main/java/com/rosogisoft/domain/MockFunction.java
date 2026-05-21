package com.rosogisoft.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "mock_functions",
        uniqueConstraints = @UniqueConstraint(columnNames = {"owner_id", "name"}))
@Getter
@Setter
@NoArgsConstructor
public class MockFunction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id", nullable = false)
    private User owner;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "signature_label", nullable = false, length = 500)
    private String signatureLabel;

    @Column(name = "return_type", nullable = false, length = 50)
    private String returnType = "string";

    @Column(name = "source_code", nullable = false, columnDefinition = "TEXT")
    private String sourceCode;

    @Column(name = "is_enabled", nullable = false)
    private boolean enabled = true;

    @Column(name = "is_shared", nullable = false)
    private boolean shared = false;

    @Column(name = "shared_at")
    private Instant sharedAt;

    @Column(nullable = false)
    private int revision = 1;

    @Column(name = "read_only", nullable = false)
    private boolean readOnly = false;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_function_id")
    private MockFunction sourceFunction;

    @Column(name = "source_revision")
    private Integer sourceRevision;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    public MockFunction(User owner,
                        String name,
                        String description,
                        String signatureLabel,
                        String returnType,
                        String sourceCode) {
        this.owner = owner;
        this.name = name;
        this.description = description;
        this.signatureLabel = signatureLabel;
        this.returnType = returnType;
        this.sourceCode = sourceCode;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }
}
