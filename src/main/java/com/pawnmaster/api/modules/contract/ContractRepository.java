package com.pawnmaster.api.modules.contract;

import com.pawnmaster.api.modules.contract.dto.CollateralCategoryGroupProjection;
import com.pawnmaster.api.modules.contract.dto.DashboardKpiProjection;
import com.pawnmaster.api.modules.contract.dto.DashboardTabCountsProjection;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface ContractRepository extends JpaRepository<ContractEntity, Long> {

    @Query("SELECT c FROM ContractEntity c LEFT JOIN FETCH c.branch LEFT JOIN FETCH c.customer LEFT JOIN FETCH c.collateral WHERE c.id = :id AND c.deleted = false")
    Optional<ContractEntity> findByIdAndDeletedFalse(@Param("id") Long id);

    @Query("SELECT c FROM ContractEntity c LEFT JOIN FETCH c.branch LEFT JOIN FETCH c.customer LEFT JOIN FETCH c.collateral WHERE c.contractCode = :contractCode AND c.deleted = false")
    Optional<ContractEntity> findByContractCodeAndDeletedFalse(@Param("contractCode") String contractCode);

    @Query("SELECT c FROM ContractEntity c LEFT JOIN FETCH c.branch LEFT JOIN FETCH c.customer LEFT JOIN FETCH c.collateral WHERE c.customer.id = :customerId AND c.deleted = false")
    List<ContractEntity> findByCustomerIdAndDeletedFalse(@Param("customerId") Long customerId);

    @Query("SELECT c FROM ContractEntity c LEFT JOIN FETCH c.branch LEFT JOIN FETCH c.customer LEFT JOIN FETCH c.collateral WHERE c.branch.id = :branchId AND c.deleted = false")
    List<ContractEntity> findByBranchIdAndDeletedFalse(@Param("branchId") Long branchId);

    @Query("SELECT c FROM ContractEntity c LEFT JOIN FETCH c.branch LEFT JOIN FETCH c.customer LEFT JOIN FETCH c.collateral WHERE c.branch.id = :branchId AND c.deleted = false ORDER BY c.createdAt DESC")
    List<ContractEntity> findByBranchIdAndDeletedFalseOrderByCreatedAtDesc(@Param("branchId") Long branchId);

    @Query("SELECT c FROM ContractEntity c LEFT JOIN FETCH c.branch LEFT JOIN FETCH c.customer LEFT JOIN FETCH c.collateral WHERE c.branch.id = :branchId AND c.status = :status AND c.deleted = false")
    List<ContractEntity> findByBranchIdAndStatusAndDeletedFalse(
            @Param("branchId") Long branchId, @Param("status") ContractStatus status);

    @Query("SELECT c FROM ContractEntity c LEFT JOIN FETCH c.branch LEFT JOIN FETCH c.customer LEFT JOIN FETCH c.collateral WHERE c.status = :status AND c.deleted = false")
    List<ContractEntity> findByStatusAndDeletedFalse(@Param("status") ContractStatus status);

    @Query("SELECT c FROM ContractEntity c LEFT JOIN FETCH c.branch LEFT JOIN FETCH c.customer LEFT JOIN FETCH c.collateral WHERE c.status = :status AND c.nextInterestDueDate < :date AND c.deleted = false")
    List<ContractEntity> findByStatusAndNextInterestDueDateBeforeAndDeletedFalse(
            @Param("status") ContractStatus status, @Param("date") LocalDate date);

    @Query("SELECT c FROM ContractEntity c LEFT JOIN FETCH c.branch LEFT JOIN FETCH c.customer LEFT JOIN FETCH c.collateral WHERE c.deleted = false ORDER BY c.createdAt DESC")
    List<ContractEntity> findByDeletedFalseOrderByCreatedAtDesc();

    @Query("""
            SELECT c FROM ContractEntity c
            LEFT JOIN FETCH c.branch
            LEFT JOIN FETCH c.customer
            LEFT JOIN FETCH c.collateral
            WHERE c.deleted = false
              AND (:branchId IS NULL OR c.branch.id = :branchId)
              AND (
                    (c.startDate BETWEEN :monthStart AND :monthEnd)
                    OR c.status = com.pawnmaster.api.modules.contract.ContractStatus.PENDING_APPROVAL
                    OR (
                        c.status IN :openStatuses
                        AND (
                            c.nextInterestDueDate <= :monthEnd
                            OR (c.maturityDate BETWEEN :monthStart AND :monthEnd)
                        )
                    )
              )
            ORDER BY c.startDate DESC, c.createdAt DESC
            """)
    List<ContractEntity> findDashboardMonthlyContracts(
            @Param("branchId") Long branchId,
            @Param("monthStart") LocalDate monthStart,
            @Param("monthEnd") LocalDate monthEnd,
            @Param("openStatuses") Collection<ContractStatus> openStatuses);

    // =========================================================================
    // MILESTONE 2: SERVER-SIDE PAGINATION & AGGREGATE DASHBOARD QUERIES
    // =========================================================================

    @Query(
            value = """
                    SELECT c FROM ContractEntity c
                    LEFT JOIN FETCH c.branch
                    LEFT JOIN FETCH c.customer
                    LEFT JOIN FETCH c.collateral
                    WHERE c.deleted = false
                      AND (:branchId IS NULL OR c.branch.id = :branchId)
                      AND (
                            (c.startDate BETWEEN :startDate AND :endDate)
                            OR (c.closedDate IS NOT NULL AND c.closedDate BETWEEN :startDate AND :endDate)
                            OR (:includeCurrentOverdue = true AND c.status = com.pawnmaster.api.modules.contract.ContractStatus.PENDING_APPROVAL)
                            OR (
                                c.status IN :openStatuses
                                AND (
                                    (:includeCurrentOverdue = true AND c.nextInterestDueDate <= :endDate)
                                    OR (:includeCurrentOverdue = false AND c.nextInterestDueDate BETWEEN :startDate AND :endDate)
                                    OR (c.maturityDate BETWEEN :startDate AND :endDate)
                                )
                            )
                      )
                      AND (
                            :tab IS NULL OR :tab = 'ALL'
                            OR (:tab = 'PENDING_APPROVAL' AND c.status = com.pawnmaster.api.modules.contract.ContractStatus.PENDING_APPROVAL)
                            OR (:tab = 'ACTIVE' AND c.status IN (com.pawnmaster.api.modules.contract.ContractStatus.ACTIVE, com.pawnmaster.api.modules.contract.ContractStatus.EXTENDED))
                            OR (:tab = 'DUE_TODAY' AND c.status IN (com.pawnmaster.api.modules.contract.ContractStatus.ACTIVE, com.pawnmaster.api.modules.contract.ContractStatus.EXTENDED) AND (c.nextInterestDueDate = :today OR c.maturityDate = :today))
                            OR (:tab = 'OVERDUE' AND c.status = com.pawnmaster.api.modules.contract.ContractStatus.OVERDUE)
                            OR (:tab = 'WAITING_LIQUIDATION' AND c.collateral.status = com.pawnmaster.api.modules.collateral.CollateralStatus.WAITING_LIQUIDATION)
                      )
                      AND (
                            :keyword IS NULL OR :keyword = ''
                            OR LOWER(c.contractCode) LIKE LOWER(CONCAT('%', :keyword, '%'))
                            OR LOWER(c.customer.fullName) LIKE LOWER(CONCAT('%', :keyword, '%'))
                            OR c.customer.phoneNumber LIKE CONCAT('%', :keyword, '%')
                            OR c.customer.identityNumber LIKE CONCAT('%', :keyword, '%')
                            OR LOWER(c.collateral.name) LIKE LOWER(CONCAT('%', :keyword, '%'))
                      )
                    ORDER BY c.startDate DESC, c.createdAt DESC
                    """,
            countQuery = """
                    SELECT COUNT(c) FROM ContractEntity c
                    LEFT JOIN c.customer cust
                    LEFT JOIN c.collateral col
                    WHERE c.deleted = false
                      AND (:branchId IS NULL OR c.branch.id = :branchId)
                      AND (
                            (c.startDate BETWEEN :startDate AND :endDate)
                            OR (c.closedDate IS NOT NULL AND c.closedDate BETWEEN :startDate AND :endDate)
                            OR (:includeCurrentOverdue = true AND c.status = com.pawnmaster.api.modules.contract.ContractStatus.PENDING_APPROVAL)
                            OR (
                                c.status IN :openStatuses
                                AND (
                                    (:includeCurrentOverdue = true AND c.nextInterestDueDate <= :endDate)
                                    OR (:includeCurrentOverdue = false AND c.nextInterestDueDate BETWEEN :startDate AND :endDate)
                                    OR (c.maturityDate BETWEEN :startDate AND :endDate)
                                )
                            )
                      )
                      AND (
                            :tab IS NULL OR :tab = 'ALL'
                            OR (:tab = 'PENDING_APPROVAL' AND c.status = com.pawnmaster.api.modules.contract.ContractStatus.PENDING_APPROVAL)
                            OR (:tab = 'ACTIVE' AND c.status IN (com.pawnmaster.api.modules.contract.ContractStatus.ACTIVE, com.pawnmaster.api.modules.contract.ContractStatus.EXTENDED))
                            OR (:tab = 'DUE_TODAY' AND c.status IN (com.pawnmaster.api.modules.contract.ContractStatus.ACTIVE, com.pawnmaster.api.modules.contract.ContractStatus.EXTENDED) AND (c.nextInterestDueDate = :today OR c.maturityDate = :today))
                            OR (:tab = 'OVERDUE' AND c.status = com.pawnmaster.api.modules.contract.ContractStatus.OVERDUE)
                            OR (:tab = 'WAITING_LIQUIDATION' AND col.status = com.pawnmaster.api.modules.collateral.CollateralStatus.WAITING_LIQUIDATION)
                      )
                      AND (
                            :keyword IS NULL OR :keyword = ''
                            OR LOWER(c.contractCode) LIKE LOWER(CONCAT('%', :keyword, '%'))
                            OR LOWER(cust.fullName) LIKE LOWER(CONCAT('%', :keyword, '%'))
                            OR cust.phoneNumber LIKE CONCAT('%', :keyword, '%')
                            OR cust.identityNumber LIKE CONCAT('%', :keyword, '%')
                            OR LOWER(col.name) LIKE LOWER(CONCAT('%', :keyword, '%'))
                      )
                    """
    )
    Page<ContractEntity> findDashboardContractPage(
            @Param("branchId") Long branchId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate,
            @Param("includeCurrentOverdue") boolean includeCurrentOverdue,
            @Param("openStatuses") Collection<ContractStatus> openStatuses,
            @Param("tab") String tab,
            @Param("today") LocalDate today,
            @Param("keyword") String keyword,
            Pageable pageable
    );

    @Query("""
            SELECT
                COUNT(c) AS allCount,
                COALESCE(SUM(CASE WHEN c.status = com.pawnmaster.api.modules.contract.ContractStatus.PENDING_APPROVAL THEN 1L ELSE 0L END), 0L) AS pendingCount,
                COALESCE(SUM(CASE WHEN c.status IN (com.pawnmaster.api.modules.contract.ContractStatus.ACTIVE, com.pawnmaster.api.modules.contract.ContractStatus.EXTENDED) THEN 1L ELSE 0L END), 0L) AS activeCount,
                COALESCE(SUM(CASE WHEN c.status IN (com.pawnmaster.api.modules.contract.ContractStatus.ACTIVE, com.pawnmaster.api.modules.contract.ContractStatus.EXTENDED) AND (c.nextInterestDueDate = :today OR c.maturityDate = :today) THEN 1L ELSE 0L END), 0L) AS dueTodayCount,
                COALESCE(SUM(CASE WHEN c.status = com.pawnmaster.api.modules.contract.ContractStatus.OVERDUE THEN 1L ELSE 0L END), 0L) AS overdueCount,
                COALESCE(SUM(CASE WHEN col.status = com.pawnmaster.api.modules.collateral.CollateralStatus.WAITING_LIQUIDATION THEN 1L ELSE 0L END), 0L) AS waitingLiquidationCount
            FROM ContractEntity c
            LEFT JOIN c.collateral col
            WHERE c.deleted = false
              AND (:branchId IS NULL OR c.branch.id = :branchId)
              AND (
                    (c.startDate BETWEEN :startDate AND :endDate)
                    OR (c.closedDate IS NOT NULL AND c.closedDate BETWEEN :startDate AND :endDate)
                    OR (:includeCurrentOverdue = true AND c.status = com.pawnmaster.api.modules.contract.ContractStatus.PENDING_APPROVAL)
                    OR (
                        c.status IN :openStatuses
                        AND (
                            (:includeCurrentOverdue = true AND c.nextInterestDueDate <= :endDate)
                            OR (:includeCurrentOverdue = false AND c.nextInterestDueDate BETWEEN :startDate AND :endDate)
                            OR (c.maturityDate BETWEEN :startDate AND :endDate)
                        )
                    )
              )
            """)
    DashboardTabCountsProjection countDashboardTabs(
            @Param("branchId") Long branchId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate,
            @Param("includeCurrentOverdue") boolean includeCurrentOverdue,
            @Param("openStatuses") Collection<ContractStatus> openStatuses,
            @Param("today") LocalDate today
    );

    @Query("""
            SELECT
                COALESCE(SUM(CASE WHEN c.status IN (com.pawnmaster.api.modules.contract.ContractStatus.ACTIVE, com.pawnmaster.api.modules.contract.ContractStatus.EXTENDED) THEN 1L ELSE 0L END), 0L) AS activeCount,
                COALESCE(SUM(CASE WHEN c.status = com.pawnmaster.api.modules.contract.ContractStatus.PENDING_APPROVAL THEN 1L ELSE 0L END), 0L) AS pendingCount,
                COALESCE(SUM(CASE WHEN c.status IN (com.pawnmaster.api.modules.contract.ContractStatus.ACTIVE, com.pawnmaster.api.modules.contract.ContractStatus.EXTENDED) THEN COALESCE(c.remainingPrincipal, c.principalAmount, 0) ELSE 0 END), 0) AS disbursedPrincipal,
                COALESCE(SUM(CASE WHEN c.status = com.pawnmaster.api.modules.contract.ContractStatus.OVERDUE THEN 1L ELSE 0L END), 0L) AS overdueCount,
                COALESCE(SUM(CASE WHEN col.status = com.pawnmaster.api.modules.collateral.CollateralStatus.WAITING_LIQUIDATION THEN 1L ELSE 0L END), 0L) AS waitingLiquidationCount,
                COALESCE(SUM(CASE WHEN c.status = com.pawnmaster.api.modules.contract.ContractStatus.OVERDUE THEN COALESCE(c.remainingPrincipal, c.principalAmount, 0) ELSE 0 END), 0) AS atRiskPrincipal
            FROM ContractEntity c
            LEFT JOIN c.collateral col
            WHERE c.deleted = false
              AND (:branchId IS NULL OR c.branch.id = :branchId)
            """)
    DashboardKpiProjection aggregatePortfolioKpis(
            @Param("branchId") Long branchId
    );

    @Query("""
            SELECT
                COALESCE(SUM(CASE WHEN c.status IN (com.pawnmaster.api.modules.contract.ContractStatus.ACTIVE, com.pawnmaster.api.modules.contract.ContractStatus.EXTENDED) THEN 1L ELSE 0L END), 0L) AS activeCount,
                COALESCE(SUM(CASE WHEN c.status = com.pawnmaster.api.modules.contract.ContractStatus.PENDING_APPROVAL THEN 1L ELSE 0L END), 0L) AS pendingCount,
                COALESCE(SUM(CASE WHEN c.status IN (com.pawnmaster.api.modules.contract.ContractStatus.ACTIVE, com.pawnmaster.api.modules.contract.ContractStatus.EXTENDED) THEN COALESCE(c.remainingPrincipal, c.principalAmount, 0) ELSE 0 END), 0) AS disbursedPrincipal,
                COALESCE(SUM(CASE WHEN c.status = com.pawnmaster.api.modules.contract.ContractStatus.OVERDUE THEN 1L ELSE 0L END), 0L) AS overdueCount,
                COALESCE(SUM(CASE WHEN col.status = com.pawnmaster.api.modules.collateral.CollateralStatus.WAITING_LIQUIDATION THEN 1L ELSE 0L END), 0L) AS waitingLiquidationCount,
                COALESCE(SUM(CASE WHEN c.status = com.pawnmaster.api.modules.contract.ContractStatus.OVERDUE THEN COALESCE(c.remainingPrincipal, c.principalAmount, 0) ELSE 0 END), 0) AS atRiskPrincipal
            FROM ContractEntity c
            LEFT JOIN c.collateral col
            WHERE c.deleted = false
              AND (:branchId IS NULL OR c.branch.id = :branchId)
              AND (
                    (c.startDate BETWEEN :startDate AND :endDate)
                    OR (c.closedDate IS NOT NULL AND c.closedDate BETWEEN :startDate AND :endDate)
                    OR (:includeCurrentOverdue = true AND c.status = com.pawnmaster.api.modules.contract.ContractStatus.PENDING_APPROVAL)
                    OR (
                        c.status IN :openStatuses
                        AND (
                            (:includeCurrentOverdue = true AND c.nextInterestDueDate <= :endDate)
                            OR (:includeCurrentOverdue = false AND c.nextInterestDueDate BETWEEN :startDate AND :endDate)
                            OR (c.maturityDate BETWEEN :startDate AND :endDate)
                        )
                    )
              )
            """)
    DashboardKpiProjection aggregatePeriodKpis(
            @Param("branchId") Long branchId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate,
            @Param("includeCurrentOverdue") boolean includeCurrentOverdue,
            @Param("openStatuses") Collection<ContractStatus> openStatuses
    );

    @Query("""
            SELECT
                col.category AS category,
                COUNT(c) AS contractCount,
                COALESCE(SUM(col.appraisedValue), 0) AS totalAppraisedValue
            FROM ContractEntity c
            JOIN c.collateral col
            WHERE c.deleted = false
              AND c.status <> com.pawnmaster.api.modules.contract.ContractStatus.CANCELLED
              AND (:branchId IS NULL OR c.branch.id = :branchId)
            GROUP BY col.category
            """)
    List<CollateralCategoryGroupProjection> aggregateCollateralDistribution(
            @Param("branchId") Long branchId
    );

    @Query("""
            SELECT COALESCE(SUM(
                CASE
                    WHEN c.interestType = com.pawnmaster.api.modules.contract.InterestType.DAILY_PER_MILLION THEN
                        (COALESCE(c.remainingPrincipal, c.principalAmount, 0) / 1000000.0)
                        * (c.interestRate + COALESCE(c.storageFeeRate, 0))
                        * COALESCE(c.paymentCycleDays, 10)
                    WHEN c.interestType = com.pawnmaster.api.modules.contract.InterestType.PERCENT_MONTHLY THEN
                        COALESCE(c.remainingPrincipal, c.principalAmount, 0)
                        * ((c.interestRate + COALESCE(c.storageFeeRate, 0)) / 100.0)
                        * (COALESCE(c.paymentCycleDays, 10) / 30.0)
                    ELSE
                        COALESCE(c.remainingPrincipal, c.principalAmount, 0)
                        * ((c.interestRate + COALESCE(c.storageFeeRate, 0)) / 100.0)
                        * (COALESCE(c.paymentCycleDays, 10) / 7.0)
                END
            ), 0)
            FROM ContractEntity c
            WHERE c.deleted = false
              AND c.status IN (
                  com.pawnmaster.api.modules.contract.ContractStatus.ACTIVE,
                  com.pawnmaster.api.modules.contract.ContractStatus.EXTENDED,
                  com.pawnmaster.api.modules.contract.ContractStatus.OVERDUE
              )
              AND (:branchId IS NULL OR c.branch.id = :branchId)
            """)
    BigDecimal sumUpcomingCycleInterest(@Param("branchId") Long branchId);
}
