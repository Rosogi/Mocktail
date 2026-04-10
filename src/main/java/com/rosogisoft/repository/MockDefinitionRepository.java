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

    /**
     * All mocks for a given user, ordered by priority desc then created_at
     */
    @Query(value = """
            SELECT * FROM mock_definitions
            WHERE owner_id = :ownerId
            ORDER BY priority DESC, created_at ASC
            """, nativeQuery = true)
    List<MockDefinition> findByOwnerId (@Param("ownerId") Long ownerId);

    /**
     * Only active mocks for matching (called on every incoming request)
     */
    @Query(value = """
            SELECT * FROM mock_definitions
            WHERE owner_id = :ownerId
              AND is_active = TRUE
            ORDER BY priority DESC, created_at ASC
            """, nativeQuery = true)
    List<MockDefinition> findActiveByOwnerId (@Param("ownerId") Long ownerId);

    /**
     * Fetch single mock ensuring it belongs to the given owner (security check)
     */
    @Query(value = """
            SELECT * FROM mock_definitions
            WHERE id = :id AND owner_id = :ownerId
            """, nativeQuery = true)
    Optional<MockDefinition> findByIdAndOwnerId (@Param("id") Long id,
                                                 @Param("ownerId") Long ownerId);

    /**
     * Toggle active flag
     */
    @Modifying
    @Query(value = """
            UPDATE mock_definitions
            SET is_active = NOT is_active, updated_at = NOW()
            WHERE id = :id AND owner_id = :ownerId
            """, nativeQuery = true)
    int toggleActive (@Param("id") Long id, @Param("ownerId") Long ownerId);

    /**
     * Delete only if owned by the user
     */
    @Modifying
    @Query(value = """
            DELETE FROM mock_definitions
            WHERE id = :id AND owner_id = :ownerId
            """, nativeQuery = true)
    int deleteByIdAndOwnerId (@Param("id") Long id, @Param("ownerId") Long ownerId);
}
