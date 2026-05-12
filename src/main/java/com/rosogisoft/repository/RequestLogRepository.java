package com.rosogisoft.repository;

import com.rosogisoft.domain.RequestLog;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RequestLogRepository extends JpaRepository<RequestLog, Long> {

    @Query(value = """
            SELECT * FROM request_logs
            WHERE owner_id = :ownerId
            ORDER BY timestamp DESC
            LIMIT :limit
            """, nativeQuery = true)
    List<RequestLog> findRecentByOwnerId (@Param("ownerId") Long ownerId,
                                          @Param("limit") int limit);

    @Query("""
    SELECT l FROM RequestLog l
    LEFT JOIN FETCH l.matchedMock
    WHERE l.owner.id = :ownerId
    ORDER BY l.timestamp DESC
""")
    List<RequestLog> findRecentByOwnerId(@Param("ownerId") Long ownerId, Pageable pageable);

    @Query(value = """
            SELECT * FROM request_logs
            WHERE user_port = :port
            ORDER BY timestamp DESC
            LIMIT :limit
            """, nativeQuery = true)
    List<RequestLog> findRecentByPort (@Param("port") int port,
                                       @Param("limit") int limit);

    @Query("""
            SELECT l FROM RequestLog l
            LEFT JOIN FETCH l.matchedMock
            WHERE l.id = :id AND l.owner.id = :ownerId
            """)
    Optional<RequestLog> findByIdAndOwnerId(@Param("id") Long id,
                                            @Param("ownerId") Long ownerId);

    @Modifying
    @Query(value = "DELETE FROM request_logs WHERE owner_id = :ownerId", nativeQuery = true)
    void deleteAllByOwnerId (@Param("ownerId") Long ownerId);

    @Modifying
    @Query(value = "DELETE FROM request_logs WHERE id = :id AND owner_id = :ownerId", nativeQuery = true)
    int deleteByIdAndOwnerId(@Param("id") Long id, @Param("ownerId") Long ownerId);

    @Query(value = """
            SELECT COUNT(*) FROM request_logs
            WHERE owner_id = :ownerId
            """, nativeQuery = true)
    long countByOwnerId (@Param("ownerId") Long ownerId);
}
