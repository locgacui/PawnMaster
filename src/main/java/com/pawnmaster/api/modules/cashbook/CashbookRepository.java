package com.pawnmaster.api.modules.cashbook;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface CashbookRepository extends JpaRepository<CashbookEntity, Long> {

    Optional<CashbookEntity> findByTransactionCodeAndDeletedFalse(String transactionCode);

    @Query("SELECT t FROM CashbookEntity t WHERE t.branch.id = :branchId AND t.deleted = false ORDER BY t.transactionDate DESC")
    List<CashbookEntity> findByBranchIdAndDeletedFalseOrderByTransactionDateDesc(@Param("branchId") Long branchId);

    @Query("SELECT t FROM CashbookEntity t WHERE t.branch.id = :branchId AND t.transactionType = :transactionType AND t.deleted = false ORDER BY t.transactionDate DESC")
    List<CashbookEntity> findByBranchIdAndTransactionTypeAndDeletedFalseOrderByTransactionDateDesc(
            @Param("branchId") Long branchId, @Param("transactionType") TransactionType transactionType);

    @Query("SELECT t FROM CashbookEntity t WHERE t.contract.id = :contractId AND t.deleted = false ORDER BY t.transactionDate DESC")
    List<CashbookEntity> findByContractIdAndDeletedFalseOrderByTransactionDateDesc(@Param("contractId") Long contractId);

    @Query("SELECT t FROM CashbookEntity t WHERE t.customer.id = :customerId AND t.deleted = false ORDER BY t.transactionDate DESC")
    List<CashbookEntity> findByCustomerIdAndDeletedFalseOrderByTransactionDateDesc(@Param("customerId") Long customerId);

    @Query("SELECT t FROM CashbookEntity t WHERE t.branch.id = :branchId AND t.transactionDate BETWEEN :startDate AND :endDate AND t.deleted = false ORDER BY t.transactionDate DESC")
    List<CashbookEntity> findByBranchIdAndTransactionDateBetweenAndDeletedFalseOrderByTransactionDateDesc(
            @Param("branchId") Long branchId, @Param("startDate") LocalDateTime startDate, @Param("endDate") LocalDateTime endDate);

    List<CashbookEntity> findByDeletedFalseOrderByTransactionDateDesc();
}
