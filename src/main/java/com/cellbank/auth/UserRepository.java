package com.cellbank.auth;

import java.util.Optional;
import java.util.List;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<User, Long> {

    @EntityGraph(attributePaths = "roles")
    @Query("select u from User u where lower(u.username) = lower(:username)")
    Optional<User> findByUsername(@Param("username") String username);

    @EntityGraph(attributePaths = "roles")
    @Query("select u from User u where lower(u.email) = lower(:email)")
    Optional<User> findByEmail(@Param("email") String email);

    @Query("select case when count(u) > 0 then true else false end "
            + "from User u where lower(u.username) = lower(:username)")
    boolean existsByUsername(@Param("username") String username);

    @Query("select case when count(u) > 0 then true else false end "
            + "from User u where lower(u.email) = lower(:email)")
    boolean existsByEmail(@Param("email") String email);

    boolean existsByRoles_Name(String roleName);
    
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.id = :id")
    Optional<User> findByIdForUpdate(@Param("id") Long id);
    
    List<User> findDistinctByStatusAndRoles_NameOrderByFullNameAscIdAsc(
            UserStatus status,
            String roleName);
    
    List<User> findDistinctByRoles_NameOrderByFullNameAscIdAsc(
            String roleName);

    Optional<User> findByIdAndRoles_Name(
            Long id,
            String roleName);
}
