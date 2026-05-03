package com.rosogisoft.repository;

import com.rosogisoft.domain.UserPasswordCredential;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserPasswordCredentialRepository extends JpaRepository<UserPasswordCredential, Long> {

    @Query("""
            SELECT c FROM UserPasswordCredential c
            JOIN FETCH c.user u
            JOIN FETCH u.role
            WHERE c.login = :login
            """)
    Optional<UserPasswordCredential> findByLogin(@Param("login") String login);

    boolean existsByLogin(String login);

    boolean existsByUser_Role_Code(String roleCode);

    long countByUser_Role_CodeAndUser_EnabledTrue(String roleCode);

    @Query("""
            SELECT c FROM UserPasswordCredential c
            JOIN FETCH c.user u
            JOIN FETCH u.role
            ORDER BY c.login
            """)
    List<UserPasswordCredential> findAllByOrderByLoginAsc();
}
