package com.rosogisoft.repository;

import com.rosogisoft.domain.LlmAccessToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface LlmAccessTokenRepository extends JpaRepository<LlmAccessToken, Long> {

    @Query("""
            SELECT t FROM LlmAccessToken t
            JOIN FETCH t.owner o
            LEFT JOIN FETCH o.role
            WHERE o.id = :ownerId
            """)
    Optional<LlmAccessToken> findByOwnerId(@Param("ownerId") Long ownerId);

    @Query("""
            SELECT t FROM LlmAccessToken t
            JOIN FETCH t.owner o
            LEFT JOIN FETCH o.role
            WHERE t.tokenHash = :tokenHash
            """)
    Optional<LlmAccessToken> findByTokenHash(@Param("tokenHash") String tokenHash);
}
