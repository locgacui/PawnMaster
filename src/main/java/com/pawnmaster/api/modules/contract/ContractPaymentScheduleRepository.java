package com.pawnmaster.api.modules.contract;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

@Repository
public interface ContractPaymentScheduleRepository extends JpaRepository<ContractPaymentScheduleEntity, Long> {

    @Query("""
            SELECT s
            FROM ContractPaymentScheduleEntity s
            LEFT JOIN FETCH s.interestPayment
            WHERE s.contract.id = :contractId
              AND s.deleted = false
            ORDER BY s.periodNumber ASC
            """)
    List<ContractPaymentScheduleEntity> findByContractIdAndDeletedFalseOrderByPeriodNumberAsc(
            @Param("contractId") Long contractId
    );

    @Query("""
            SELECT s
            FROM ContractPaymentScheduleEntity s
            LEFT JOIN FETCH s.interestPayment
            WHERE s.contract.id IN :contractIds
              AND s.deleted = false
            ORDER BY s.contract.id ASC, s.periodNumber ASC
            """)
    List<ContractPaymentScheduleEntity> findByContractIdInAndDeletedFalseOrderByContractIdAscPeriodNumberAsc(
            @Param("contractIds") Collection<Long> contractIds
    );

    @Query("""
            SELECT s
            FROM ContractPaymentScheduleEntity s
            WHERE s.deleted = false
              AND s.status = com.pawnmaster.api.modules.contract.ScheduleStatus.SCHEDULED
              AND s.dueDate < :asOfDate
              AND s.contract.deleted = false
              AND s.contract.status IN (
                  com.pawnmaster.api.modules.contract.ContractStatus.ACTIVE,
                  com.pawnmaster.api.modules.contract.ContractStatus.EXTENDED,
                  com.pawnmaster.api.modules.contract.ContractStatus.OVERDUE
              )
            """)
    List<ContractPaymentScheduleEntity> findOpenSchedulesDueBefore(@Param("asOfDate") LocalDate asOfDate);
}

