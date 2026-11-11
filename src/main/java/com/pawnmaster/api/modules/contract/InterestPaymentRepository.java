package com.pawnmaster.api.modules.contract;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface InterestPaymentRepository extends JpaRepository<InterestPaymentEntity, Long> {

    Optional<InterestPaymentEntity> findByIdAndDeletedFalse(Long id);

    Optional<InterestPaymentEntity> findByPaymentCodeAndDeletedFalse(String paymentCode);

    @Query("SELECT p FROM InterestPaymentEntity p LEFT JOIN FETCH p.contract WHERE p.contract.id = :contractId AND p.deleted = false ORDER BY p.paymentDate DESC")
    List<InterestPaymentEntity> findByContractIdAndDeletedFalseOrderByPaymentDateDesc(@Param("contractId") Long contractId);

    @Query("""
            SELECT COALESCE(SUM(p.interestAmount + p.storageFeeAmount + p.penaltyAmount), 0)
            FROM InterestPaymentEntity p
            WHERE p.deleted = false
              AND (:branchId IS NULL OR p.contract.branch.id = :branchId)
              AND p.paymentDate >= :fromTime
              AND p.paymentDate < :toTime
            """)
    BigDecimal sumCollectedInterestInPeriod(
            @Param("branchId") Long branchId,
            @Param("fromTime") LocalDateTime fromTime,
            @Param("toTime") LocalDateTime toTime);
}
