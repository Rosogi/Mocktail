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
@Table(name = "collection_subscriptions",
        uniqueConstraints = {
                @UniqueConstraint(columnNames = {"subscriber_id", "source_collection_id"}),
                @UniqueConstraint(columnNames = {"local_collection_id"})
        })
@Getter
@Setter
@NoArgsConstructor
public class CollectionSubscription {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "subscriber_id", nullable = false)
    private User subscriber;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_collection_id")
    private MockCollection sourceCollection;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "local_collection_id", nullable = false)
    private MockCollection localCollection;

    @Column(name = "source_revision", nullable = false)
    private int sourceRevision = 1;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public CollectionSubscription(User subscriber,
                                  MockCollection sourceCollection,
                                  MockCollection localCollection,
                                  int sourceRevision) {
        this.subscriber = subscriber;
        this.sourceCollection = sourceCollection;
        this.localCollection = localCollection;
        this.sourceRevision = sourceRevision;
    }
}
