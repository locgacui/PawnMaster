package com.pawnmaster.api.modules.customer;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CustomerRepository extends JpaRepository<CustomerEntity, Long> {

    Optional<CustomerEntity> findByIdAndDeletedFalse(Long id);

    Optional<CustomerEntity> findByCustomerCodeAndDeletedFalse(String customerCode);

    Optional<CustomerEntity> findByIdentityNumberAndDeletedFalse(String identityNumber);

    List<CustomerEntity> findAllByDeletedFalse();

    @Query("SELECT c FROM CustomerEntity c WHERE c.branch.id = :branchId AND c.deleted = false")
    List<CustomerEntity> findByBranchIdAndDeletedFalse(@Param("branchId") Long branchId);

    boolean existsByIdentityNumber(String identityNumber);

    boolean existsByIdentityNumberAndDeletedFalse(String identityNumber);

    boolean existsByPhoneNumber(String phoneNumber);
}
