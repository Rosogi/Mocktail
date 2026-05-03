package com.rosogisoft.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "mock_collections",
        uniqueConstraints = @UniqueConstraint(columnNames = {"owner_id", "name"}))
@Getter
@Setter
@NoArgsConstructor
public class MockCollection {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id", nullable = false)
    private User owner;

    @Column(nullable = false)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "is_shared", nullable = false)
    private boolean shared = false;

    @Column(name = "shared_at")
    private Instant sharedAt;

    @Column(nullable = false)
    private int revision = 1;

    @Column(name = "read_only", nullable = false)
    private boolean readOnly = false;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_collection_id")
    private MockCollection sourceCollection;

    @Column(name = "source_revision")
    private Integer sourceRevision;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @PreUpdate
    void onUpdate() { updatedAt = Instant.now(); }

    public MockCollection(User owner, String name, String description) {
        this.owner       = owner;
        this.name        = name;
        this.description = description;
    }
}
