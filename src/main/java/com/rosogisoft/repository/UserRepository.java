package com.rosogisoft.repository;

import com.rosogisoft.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.Set;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    @Query(value = "SELECT * FROM users WHERE username = :username", nativeQuery = true)
    Optional<User> findByUsername (@Param("username") String username);

    @Query(value = "SELECT * FROM users WHERE assigned_port = :port", nativeQuery = true)
    Optional<User> findByAssignedPort (@Param("port") int port);

    @Query(value = "SELECT assigned_port FROM users WHERE assigned_port IS NOT NULL", nativeQuery = true)
    Set<Integer> findAllAssignedPorts ();

    @Query(value = "SELECT * FROM users WHERE assigned_port IS NOT NULL ORDER BY username", nativeQuery = true)
    List<User> findAllWithAssignedPort ();
}
