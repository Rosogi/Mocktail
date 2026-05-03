package com.rosogisoft.repository;

import com.rosogisoft.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.Set;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByAssignedPort(int port);

    @Query("SELECT u.assignedPort FROM User u WHERE u.assignedPort IS NOT NULL")
    Set<Integer> findAllAssignedPorts ();

    @Query("""
            SELECT u FROM User u
            LEFT JOIN FETCH u.role
            WHERE u.assignedPort IS NOT NULL
            ORDER BY u.displayName
            """)
    List<User> findAllWithAssignedPort ();
}
