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

    @Query(value = """
            SELECT * FROM mock_collections
            WHERE owner_id = :ownerId
            ORDER BY name
            """, nativeQuery = true)
    List<MockCollection> findByOwnerId(@Param("ownerId") Long ownerId);

    @Query(value = """
            SELECT * FROM mock_collections
            WHERE id = :id AND owner_id = :ownerId
            """, nativeQuery = true)
    Optional<MockCollection> findByIdAndOwnerId(@Param("id") Long id,
                                                @Param("ownerId") Long ownerId);

    @Modifying
    @Query(value = """
            DELETE FROM mock_collections
            WHERE id = :id AND owner_id = :ownerId
            """, nativeQuery = true)
    int deleteByIdAndOwnerId(@Param("id") Long id, @Param("ownerId") Long ownerId);
}