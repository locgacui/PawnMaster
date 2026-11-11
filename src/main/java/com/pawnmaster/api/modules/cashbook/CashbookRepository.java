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

    @Query("SELECT t FROM CashbookEntity t LEFT JOIN FETCH t.branch LEFT JOIN FETCH t.contract LEFT JOIN FETCH t.customer WHERE t.transactionCode = :transactionCode AND t.deleted = false")
    Optional<CashbookEntity> findByTransactionCodeAndDeletedFalse(@Param("transactionCode") String transactionCode);

    @Query("SELECT t FROM CashbookEntity t LEFT JOIN FETCH t.branch LEFT JOIN FETCH t.contract LEFT JOIN FETCH t.customer WHERE t.branch.id = :branchId AND t.deleted = false ORDER BY t.transactionDate DESC")
    List<CashbookEntity> findByBranchIdAndDeletedFalseOrderByTransactionDateDesc(@Param("branchId") Long branchId);

    @Query("SELECT t FROM CashbookEntity t LEFT JOIN FETCH t.branch LEFT JOIN FETCH t.contract LEFT JOIN FETCH t.customer WHERE t.branch.id = :branchId AND t.transactionType = :transactionType AND t.deleted = false ORDER BY t.transactionDate DESC")
    List<CashbookEntity> findByBranchIdAndTransactionTypeAndDeletedFalseOrderByTransactionDateDesc(
            @Param("branchId") Long branchId, @Param("transactionType") TransactionType transactionType);

    @Query("SELECT t FROM CashbookEntity t LEFT JOIN FETCH t.branch LEFT JOIN FETCH t.contract LEFT JOIN FETCH t.customer WHERE t.contract.id = :contractId AND t.deleted = false ORDER BY t.transactionDate DESC")
    List<CashbookEntity> findByContractIdAndDeletedFalseOrderByTransactionDateDesc(@Param("contractId") Long contractId);

    @Query("SELECT t FROM CashbookEntity t LEFT JOIN FETCH t.branch LEFT JOIN FETCH t.contract LEFT JOIN FETCH t.customer WHERE t.customer.id = :customerId AND t.deleted = false ORDER BY t.transactionDate DESC")
    List<CashbookEntity> findByCustomerIdAndDeletedFalseOrderByTransactionDateDesc(@Param("customerId") Long customerId);

    @Query("SELECT t FROM CashbookEntity t LEFT JOIN FETCH t.branch LEFT JOIN FETCH t.contract LEFT JOIN FETCH t.customer WHERE t.branch.id = :branchId AND t.transactionDate BETWEEN :startDate AND :endDate AND t.deleted = false ORDER BY t.transactionDate DESC")
    List<CashbookEntity> findByBranchIdAndTransactionDateBetweenAndDeletedFalseOrderByTransactionDateDesc(
            @Param("branchId") Long branchId, @Param("startDate") LocalDateTime startDate, @Param("endDate") LocalDateTime endDate);

    @Query("SELECT t FROM CashbookEntity t LEFT JOIN FETCH t.branch LEFT JOIN FETCH t.contract LEFT JOIN FETCH t.customer WHERE t.deleted = false ORDER BY t.transactionDate DESC")
    List<CashbookEntity> findByDeletedFalseOrderByTransactionDateDesc();

    @Query("""
            SELECT t FROM CashbookEntity t
            LEFT JOIN FETCH t.branch
            LEFT JOIN FETCH t.contract
            LEFT JOIN FETCH t.customer
            WHERE t.deleted = false
              AND (:branchId IS NULL OR t.branch.id = :branchId)
              AND t.transactionDate >= :fromTime
              AND t.transactionDate < :toTime
            ORDER BY t.transactionDate DESC
            """)
    List<CashbookEntity> findByScopeAndTransactionDateBetween(
            @Param("branchId") Long branchId,
            @Param("fromTime") LocalDateTime fromTime,
            @Param("toTime") LocalDateTime toTime
    );
}
