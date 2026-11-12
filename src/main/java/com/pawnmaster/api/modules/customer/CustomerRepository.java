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

    @Query("SELECT DISTINCT c FROM CustomerEntity c WHERE c.deleted = false AND EXISTS (SELECT 1 FROM com.pawnmaster.api.modules.contract.ContractEntity ct WHERE ct.customer = c AND ct.branch.id = :branchId AND ct.deleted = false)")
    List<CustomerEntity> findByBranchIdAndDeletedFalse(@Param("branchId") Long branchId);

    boolean existsByIdentityNumber(String identityNumber);

    boolean existsByIdentityNumberAndDeletedFalse(String identityNumber);

    boolean existsByPhoneNumber(String phoneNumber);
}
