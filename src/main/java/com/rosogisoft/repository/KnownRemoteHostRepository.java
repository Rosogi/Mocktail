package com.rosogisoft.repository;

import com.rosogisoft.domain.KnownRemoteHost;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface KnownRemoteHostRepository extends JpaRepository<KnownRemoteHost, Long> {

    @Query("""
            SELECT h FROM KnownRemoteHost h
            WHERE h.owner.id = :ownerId
            ORDER BY h.displayName ASC, h.address ASC
            """)
    List<KnownRemoteHost> findByOwnerId(@Param("ownerId") Long ownerId);

    @Query("""
            SELECT h FROM KnownRemoteHost h
            WHERE h.owner.id = :ownerId
              AND LOWER(h.address) = LOWER(:address)
            """)
    Optional<KnownRemoteHost> findByOwnerIdAndAddress(@Param("ownerId") Long ownerId,
                                                      @Param("address") String address);

    @Query("""
            SELECT h FROM KnownRemoteHost h
            WHERE h.owner.id = :ownerId
              AND LOWER(h.address) IN (:addresses)
            """)
    List<KnownRemoteHost> findByOwnerIdAndAddresses(@Param("ownerId") Long ownerId,
                                                    @Param("addresses") List<String> addresses);

    @Query("""
            SELECT h FROM KnownRemoteHost h
            WHERE h.owner.id = :ownerId
              AND (
                    LOWER(h.address) LIKE LOWER(CONCAT('%', :search, '%'))
                 OR LOWER(h.displayName) LIKE LOWER(CONCAT('%', :search, '%'))
                 OR LOWER(COALESCE(h.description, '')) LIKE LOWER(CONCAT('%', :search, '%'))
              )
            ORDER BY h.displayName ASC, h.address ASC
            """)
    List<KnownRemoteHost> findMatching(@Param("ownerId") Long ownerId,
                                       @Param("search") String search);

    @Modifying
    @Query(value = """
            DELETE FROM known_remote_hosts
            WHERE owner_id = :ownerId
            """, nativeQuery = true)
    int deleteAllByOwnerId(@Param("ownerId") Long ownerId);
}
