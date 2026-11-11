package com.pawnmaster.api.modules.contract;

import com.pawnmaster.api.modules.contract.dto.ContractCreateRequest;
import com.pawnmaster.api.modules.contract.dto.ContractRedeemRequest;
import com.pawnmaster.api.modules.contract.dto.ContractResponse;
import com.pawnmaster.api.modules.contract.dto.DashboardKpiProjection;
import com.pawnmaster.api.modules.contract.dto.InterestCalculationResponse;
import com.pawnmaster.api.modules.contract.dto.InterestCollectionRequest;
import com.pawnmaster.api.modules.contract.dto.InterestPaymentResponse;
import com.pawnmaster.api.modules.contract.dto.LiquidationRequest;
import com.pawnmaster.api.modules.contract.dto.QuickContractOriginationRequest;
import com.pawnmaster.api.modules.dashboard.DashboardContractTab;
import com.pawnmaster.api.modules.dashboard.DashboardPeriod;
import com.pawnmaster.api.modules.dashboard.dto.DashboardSummaryResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;

public interface ContractService {

    // Maker step
    ContractResponse createContract(ContractCreateRequest req, Long staffUserId);

    ContractResponse originateFullContract(QuickContractOriginationRequest req, Long staffUserId);

    // Checker steps
    ContractResponse approveAndDisburseContract(Long contractId, Long managerUserId, String notes);

    ContractResponse rejectContract(Long contractId, Long managerUserId, String reason);

    // Servicing & Calculations
    InterestCalculationResponse calculateInterest(ContractEntity contract, LocalDate fromDate, LocalDate toDate);

    InterestCalculationResponse calculateContractInterest(Long contractId, LocalDate fromDate, LocalDate toDate);

    InterestPaymentResponse collectInterest(Long contractId, InterestCollectionRequest req, Long userId);

    ContractResponse redeemContract(Long contractId, Long userId, String notes);

    ContractResponse redeemContract(Long contractId, ContractRedeemRequest req, Long userId);

    ContractResponse liquidateContract(Long contractId, LiquidationRequest req, Long userId);

    int markOverdueContracts();

    int markOverdueContracts(LocalDate asOfDate);

    // Queries
    ContractResponse getContractById(Long id);

    ContractResponse getContractByCode(String contractCode);

    List<ContractResponse> getAllContracts();

    List<ContractResponse> getContractsByBranch(Long branchId);

    List<ContractResponse> getContractsByBranchAndStatus(Long branchId, ContractStatus status);

    List<ContractResponse> getContractsByCustomer(Long customerId);

    List<ContractResponse> getContractsByStatus(ContractStatus status);

    List<ContractResponse> getDashboardMonthlyContracts(Long branchId, YearMonth targetMonth);

    BigDecimal getCollectedInterestForMonth(Long branchId, YearMonth targetMonth);

    List<InterestPaymentResponse> getContractPayments(Long contractId);

    // =========================================================================
    // MILESTONE 2: SERVER-SIDE PAGINATION & AGGREGATE DASHBOARD QUERIES
    // =========================================================================

    Page<ContractResponse> findDashboardContractPage(
            Long branchId,
            DashboardPeriod period,
            DashboardContractTab tab,
            String keyword,
            Pageable pageable
    );

    Map<String, Long> getDashboardTabCounts(
            Long branchId,
            DashboardPeriod period
    );

    DashboardKpiProjection getPortfolioKpi(Long branchId);

    DashboardKpiProjection getPeriodKpi(
            Long branchId,
            DashboardPeriod period
    );

    List<DashboardSummaryResponse.CollateralCategoryShare> getDashboardCollateralDistribution(
            Long branchId
    );

    BigDecimal getUpcomingCycleInterest(
            Long branchId,
            DashboardPeriod period
    );

    BigDecimal getCollectedInterestInPeriod(
            Long branchId,
            LocalDateTime fromTime,
            LocalDateTime toTime
    );
}
