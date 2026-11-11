package com.pawnmaster.api.modules.user;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<UserEntity, Long> {

    @Query("SELECT u FROM UserEntity u LEFT JOIN FETCH u.branch b LEFT JOIN FETCH b.province WHERE u.username = :username AND u.deleted = false")
    Optional<UserEntity> findByUsernameAndDeletedFalse(@Param("username") String username);

    @Query("SELECT u FROM UserEntity u LEFT JOIN FETCH u.branch b LEFT JOIN FETCH b.province WHERE u.id = :id AND u.deleted = false")
    Optional<UserEntity> findByIdAndDeletedFalse(@Param("id") Long id);

    @Query("SELECT u FROM UserEntity u WHERE u.branch.id = :branchId AND u.deleted = false")
    List<UserEntity> findByBranchIdAndDeletedFalse(@Param("branchId") Long branchId);

    List<UserEntity> findAllByDeletedFalse();

    boolean existsByUsername(String username);

    boolean existsByUsernameAndDeletedFalse(String username);

    boolean existsByEmail(String email);

    boolean existsByEmailAndDeletedFalse(String email);
}
