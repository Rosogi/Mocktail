package com.rosogisoft.repository;

import com.rosogisoft.domain.MockDefinition;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MockDefinitionRepository extends JpaRepository<MockDefinition, Long> {

    /** All mocks for a given user, ordered by priority desc then created_at */
    @Query(value = """
        SELECT m FROM MockDefinition m
        LEFT JOIN FETCH m.collection
        WHERE m.owner.id = :ownerId
        ORDER BY m.priority DESC, m.createdAt ASC
        """)
    List<MockDefinition> findByOwnerId(@Param("ownerId") Long ownerId);

    /** Only active mocks for matching (called on every incoming request) */
    @Query(value = """
        SELECT m FROM MockDefinition m
        LEFT JOIN FETCH m.collection
        WHERE m.owner.id = :ownerId AND m.active = TRUE
        ORDER BY m.priority DESC, m.createdAt ASC
        """)
    List<MockDefinition> findActiveByOwnerId(@Param("ownerId") Long ownerId);

    /** Fetch single mock ensuring it belongs to the given owner (security check) */
    @Query("""
            SELECT m FROM MockDefinition m
            LEFT JOIN FETCH m.collection
            WHERE m.id = :id AND m.owner.id = :ownerId
            """)
    Optional<MockDefinition> findByIdAndOwnerId(@Param("id") Long id,
                                                @Param("ownerId") Long ownerId);

    /** Toggle active flag */
    @Modifying
    @Query(value = """
            UPDATE mock_definitions
            SET is_active = NOT is_active, updated_at = NOW()
            WHERE id = :id AND owner_id = :ownerId
            """, nativeQuery = true)
    int toggleActive(@Param("id") Long id, @Param("ownerId") Long ownerId);

    /** All mocks belonging to a specific collection */
    @Query("""
            SELECT m FROM MockDefinition m
            LEFT JOIN FETCH m.collection
            WHERE m.collection.id = :collectionId
            ORDER BY m.priority DESC, m.createdAt ASC
            """)
    List<MockDefinition> findByCollectionId(@Param("collectionId") Long collectionId);

    @Query("""
            SELECT COUNT(m) FROM MockDefinition m
            WHERE m.collection.id = :collectionId
            """)
    long countByCollectionId(@Param("collectionId") Long collectionId);

    /** Toggle active for all mocks in a collection owned by user */
    @Modifying
    @Query(value = """
            UPDATE mock_definitions
            SET is_active = :active, updated_at = NOW()
            WHERE collection_id = :collectionId
              AND owner_id = :ownerId
            """, nativeQuery = true)
    int setActiveForCollection(@Param("collectionId") Long collectionId,
                               @Param("ownerId") Long ownerId,
                               @Param("active") boolean active);

    @Modifying
    @Query(value = """
            DELETE FROM mock_definitions
            WHERE collection_id = :collectionId AND owner_id = :ownerId
            """, nativeQuery = true)
    int deleteByCollectionIdAndOwnerId(@Param("collectionId") Long collectionId,
                                       @Param("ownerId") Long ownerId);

    /** Delete only if owned by the user */
    @Modifying
    @Query(value = """
            DELETE FROM mock_definitions
            WHERE id = :id AND owner_id = :ownerId
            """, nativeQuery = true)
    int deleteByIdAndOwnerId(@Param("id") Long id, @Param("ownerId") Long ownerId);
}
