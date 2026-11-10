package com.pawnmaster.api.modules.contract;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface ContractRepository extends JpaRepository<ContractEntity, Long> {

    Optional<ContractEntity> findByIdAndDeletedFalse(Long id);

    Optional<ContractEntity> findByContractCodeAndDeletedFalse(String contractCode);

    @Query("SELECT c FROM ContractEntity c WHERE c.customer.id = :customerId AND c.deleted = false")
    List<ContractEntity> findByCustomerIdAndDeletedFalse(@Param("customerId") Long customerId);

    @Query("SELECT c FROM ContractEntity c WHERE c.branch.id = :branchId AND c.deleted = false")
    List<ContractEntity> findByBranchIdAndDeletedFalse(@Param("branchId") Long branchId);

    @Query("SELECT c FROM ContractEntity c WHERE c.branch.id = :branchId AND c.deleted = false ORDER BY c.createdAt DESC")
    List<ContractEntity> findByBranchIdAndDeletedFalseOrderByCreatedAtDesc(@Param("branchId") Long branchId);

    @Query("SELECT c FROM ContractEntity c WHERE c.branch.id = :branchId AND c.status = :status AND c.deleted = false")
    List<ContractEntity> findByBranchIdAndStatusAndDeletedFalse(
            @Param("branchId") Long branchId, @Param("status") ContractStatus status);

    List<ContractEntity> findByStatusAndDeletedFalse(ContractStatus status);

    List<ContractEntity> findByStatusAndNextInterestDueDateBeforeAndDeletedFalse(
            ContractStatus status, LocalDate date);

    List<ContractEntity> findByDeletedFalseOrderByCreatedAtDesc();
}
