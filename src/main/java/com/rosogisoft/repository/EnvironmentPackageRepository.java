package com.rosogisoft.repository;

import com.rosogisoft.domain.EnvironmentPackage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface EnvironmentPackageRepository extends JpaRepository<EnvironmentPackage, Long> {

    @Query("""
            SELECT p FROM EnvironmentPackage p
            WHERE p.owner.id = :ownerId
            ORDER BY p.name
            """)
    List<EnvironmentPackage> findByOwnerId(@Param("ownerId") Long ownerId);

    @Query("""
            SELECT p FROM EnvironmentPackage p
            WHERE p.id = :id AND p.owner.id = :ownerId
            """)
    Optional<EnvironmentPackage> findByIdAndOwnerId(@Param("id") Long id,
                                                    @Param("ownerId") Long ownerId);

    @Query("""
            SELECT p FROM EnvironmentPackage p
            WHERE p.owner.id = :ownerId AND LOWER(p.name) = LOWER(:name)
            """)
    Optional<EnvironmentPackage> findByOwnerIdAndNameIgnoreCase(@Param("ownerId") Long ownerId,
                                                                @Param("name") String name);

    @Query("""
            SELECT CASE WHEN COUNT(p) > 0 THEN TRUE ELSE FALSE END FROM EnvironmentPackage p
            WHERE p.owner.id = :ownerId
              AND LOWER(p.name) = LOWER(:name)
              AND (:excludeId IS NULL OR p.id <> :excludeId)
            """)
    boolean existsNameForOwner(@Param("ownerId") Long ownerId,
                               @Param("name") String name,
                               @Param("excludeId") Long excludeId);

    @Modifying
    @Query(value = """
            DELETE FROM environment_packages
            WHERE id = :id AND owner_id = :ownerId
            """, nativeQuery = true)
    int deleteByIdAndOwnerId(@Param("id") Long id, @Param("ownerId") Long ownerId);
}
