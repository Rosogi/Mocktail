package com.rosogisoft.repository;

import com.rosogisoft.domain.CollectionSubscription;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CollectionSubscriptionRepository extends JpaRepository<CollectionSubscription, Long> {

    @Query("""
            SELECT s FROM CollectionSubscription s
            LEFT JOIN FETCH s.sourceCollection source
            LEFT JOIN FETCH source.owner
            JOIN FETCH s.localCollection local
            WHERE s.subscriber.id = :subscriberId
            ORDER BY local.name
            """)
    List<CollectionSubscription> findBySubscriberId(@Param("subscriberId") Long subscriberId);

    @Query("""
            SELECT s FROM CollectionSubscription s
            LEFT JOIN FETCH s.sourceCollection source
            LEFT JOIN FETCH source.owner
            JOIN FETCH s.localCollection local
            WHERE s.subscriber.id = :subscriberId
              AND s.sourceCollection.id = :sourceCollectionId
            """)
    Optional<CollectionSubscription> findBySubscriberIdAndSourceCollectionId(
            @Param("subscriberId") Long subscriberId,
            @Param("sourceCollectionId") Long sourceCollectionId);

    @Query("""
            SELECT s FROM CollectionSubscription s
            LEFT JOIN FETCH s.sourceCollection source
            LEFT JOIN FETCH source.owner
            JOIN FETCH s.localCollection local
            WHERE s.subscriber.id = :subscriberId
              AND s.localCollection.id = :localCollectionId
            """)
    Optional<CollectionSubscription> findBySubscriberIdAndLocalCollectionId(
            @Param("subscriberId") Long subscriberId,
            @Param("localCollectionId") Long localCollectionId);
}
