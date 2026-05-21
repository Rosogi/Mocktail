package com.rosogisoft.repository;

import com.rosogisoft.domain.MockFunction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface MockFunctionRepository extends JpaRepository<MockFunction, Long> {

    @Query("""
            SELECT f FROM MockFunction f
            WHERE f.owner.id = :ownerId
            ORDER BY f.name ASC
            """)
    List<MockFunction> findByOwnerId(@Param("ownerId") Long ownerId);

    Optional<MockFunction> findByIdAndOwnerId(Long id, Long ownerId);

    Optional<MockFunction> findByOwnerIdAndName(Long ownerId, String name);

    boolean existsByOwnerIdAndName(Long ownerId, String name);

    @Query("""
            SELECT CASE WHEN COUNT(f) > 0 THEN true ELSE false END
            FROM MockFunction f
            WHERE f.owner.id = :ownerId
              AND f.name = :name
              AND (:excludeId IS NULL OR f.id <> :excludeId)
            """)
    boolean existsNameForOwner(@Param("ownerId") Long ownerId,
                               @Param("name") String name,
                               @Param("excludeId") Long excludeId);

    @Query("""
            SELECT f FROM MockFunction f
            WHERE f.shared = true
              AND f.owner.id <> :ownerId
            ORDER BY f.owner.displayName ASC, f.name ASC
            """)
    List<MockFunction> findSharedExcludingOwner(@Param("ownerId") Long ownerId);

    @Query("""
            SELECT f FROM MockFunction f
            WHERE f.id = :id
              AND f.shared = true
            """)
    Optional<MockFunction> findSharedById(@Param("id") Long id);

    List<MockFunction> findByOwnerIdAndSourceFunctionId(Long ownerId, Long sourceFunctionId);

    @Modifying
    @Query("DELETE FROM MockFunction f WHERE f.id = :id AND f.owner.id = :ownerId")
    int deleteByIdAndOwnerId(@Param("id") Long id, @Param("ownerId") Long ownerId);
}
