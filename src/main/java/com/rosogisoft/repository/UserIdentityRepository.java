package com.rosogisoft.repository;

import com.rosogisoft.domain.UserIdentity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserIdentityRepository extends JpaRepository<UserIdentity, Long> {

    @Query("""
            SELECT i FROM UserIdentity i
            JOIN FETCH i.user u
            LEFT JOIN FETCH u.role
            JOIN FETCH i.authProvider p
            WHERE p.code = :providerCode
              AND i.externalSubject = :externalSubject
            """)
    Optional<UserIdentity> findByAuthProvider_CodeAndExternalSubject(@Param("providerCode") String providerCode,
                                                                     @Param("externalSubject") String externalSubject);
}
