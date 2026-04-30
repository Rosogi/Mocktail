package com.rosogisoft.repository;

import com.rosogisoft.domain.MockCollection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MockCollectionRepository extends JpaRepository<MockCollection, Long> {

    @Query("""
            SELECT c FROM MockCollection c
            LEFT JOIN FETCH c.owner
            LEFT JOIN FETCH c.sourceCollection sc
            LEFT JOIN FETCH sc.owner
            WHERE c.owner.id = :ownerId
            ORDER BY c.name
            """)
    List<MockCollection> findByOwnerId(@Param("ownerId") Long ownerId);

    @Query("""
            SELECT c FROM MockCollection c
            LEFT JOIN FETCH c.owner
            LEFT JOIN FETCH c.sourceCollection sc
            LEFT JOIN FETCH sc.owner
            WHERE c.id = :id AND c.owner.id = :ownerId
            """)
    Optional<MockCollection> findByIdAndOwnerId(@Param("id") Long id,
                                                @Param("ownerId") Long ownerId);

    @Query("""
            SELECT c FROM MockCollection c
            WHERE c.owner.id = :ownerId AND c.readOnly = FALSE
            ORDER BY c.name
            """)
    List<MockCollection> findEditableByOwnerId(@Param("ownerId") Long ownerId);

    @Query("""
            SELECT c FROM MockCollection c
            JOIN FETCH c.owner
            WHERE c.shared = TRUE AND c.owner.id <> :ownerId
            ORDER BY c.updatedAt DESC, c.name
            """)
    List<MockCollection> findSharedExcludingOwner(@Param("ownerId") Long ownerId);

    @Query("""
            SELECT c FROM MockCollection c
            JOIN FETCH c.owner
            WHERE c.id = :id AND c.shared = TRUE
            """)
    Optional<MockCollection> findSharedById(@Param("id") Long id);

    @Query("""
            SELECT CASE WHEN COUNT(c) > 0 THEN TRUE ELSE FALSE END FROM MockCollection c
            WHERE c.owner.id = :ownerId
              AND LOWER(c.name) = LOWER(:name)
              AND (:excludeId IS NULL OR c.id <> :excludeId)
            """)
    boolean existsNameForOwner(@Param("ownerId") Long ownerId,
                               @Param("name") String name,
                               @Param("excludeId") Long excludeId);

    @Modifying
    @Query(value = """
            UPDATE mock_collections
            SET revision = revision + 1, updated_at = NOW()
            WHERE id = :id AND read_only = FALSE
            """, nativeQuery = true)
    int incrementRevision(@Param("id") Long id);

    @Modifying
    @Query(value = """
            DELETE FROM mock_collections
            WHERE id = :id AND owner_id = :ownerId
            """, nativeQuery = true)
    int deleteByIdAndOwnerId(@Param("id") Long id, @Param("ownerId") Long ownerId);
}
