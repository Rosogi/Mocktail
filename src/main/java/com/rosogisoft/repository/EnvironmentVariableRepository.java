package com.rosogisoft.repository;

import com.rosogisoft.domain.EnvironmentVariable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface EnvironmentVariableRepository extends JpaRepository<EnvironmentVariable, Long> {

    @Query("""
            SELECT v FROM EnvironmentVariable v
            WHERE v.owner.id = :ownerId AND v.environmentPackage IS NULL
            ORDER BY v.sortOrder ASC, v.id ASC
            """)
    List<EnvironmentVariable> findGlobalsByOwnerId(@Param("ownerId") Long ownerId);

    @Query("""
            SELECT v FROM EnvironmentVariable v
            WHERE v.owner.id = :ownerId AND v.environmentPackage.id = :packageId
            ORDER BY v.sortOrder ASC, v.id ASC
            """)
    List<EnvironmentVariable> findByOwnerIdAndPackageId(@Param("ownerId") Long ownerId,
                                                        @Param("packageId") Long packageId);

    @Query("""
            SELECT COUNT(v) FROM EnvironmentVariable v
            WHERE v.environmentPackage.id = :packageId
            """)
    long countByPackageId(@Param("packageId") Long packageId);

    @Modifying
    @Query(value = """
            DELETE FROM environment_variables
            WHERE owner_id = :ownerId AND package_id IS NULL
            """, nativeQuery = true)
    int deleteGlobalsByOwnerId(@Param("ownerId") Long ownerId);

    @Modifying
    @Query(value = """
            DELETE FROM environment_variables
            WHERE owner_id = :ownerId AND package_id = :packageId
            """, nativeQuery = true)
    int deleteByOwnerIdAndPackageId(@Param("ownerId") Long ownerId,
                                    @Param("packageId") Long packageId);
}
