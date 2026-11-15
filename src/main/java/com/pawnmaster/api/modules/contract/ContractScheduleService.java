package com.pawnmaster.api.modules.contract;

import com.pawnmaster.api.modules.contract.dto.ContractPaymentScheduleResponse;
import com.pawnmaster.api.modules.contract.dto.ContractResponse;
import com.pawnmaster.api.modules.contract.dto.ContractSettlementPreviewResponse;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

/**
 * Manages persisted periodic payment schedules ({@code dbo.contract_payment_schedules})
 * across contract creation, approval/disbursement, periodic interest collection,
 * partial principal paydown, early redemption, overdue scanning, and liquidation.
 */
public interface ContractScheduleService {

    void setClock(Clock clock);

    List<ContractPaymentScheduleResponse> generateInitialSchedule(ContractEntity contract);

    List<ContractPaymentScheduleResponse> activateScheduleOnApproval(ContractEntity contract, LocalDate approvalDate);

    void cancelScheduleOnRejection(ContractEntity contract, String reason);

    void applyCollection(
            ContractEntity contract,
            InterestPaymentEntity savedPayment,
            PaymentType paymentType,
            BigDecimal oldRemainingPrincipal,
            BigDecimal newRemainingPrincipal,
            LocalDate paymentDate
    );

    ContractSettlementPreviewResponse settleScheduleOnRedemption(
            ContractEntity contract,
            InterestPaymentEntity savedRedemptionPayment,
            BigDecimal preRedemptionPrincipal,
            LocalDate redemptionDate
    );

    void closeScheduleOnLiquidation(ContractEntity contract, LocalDate liquidationDate, String reason);

    int markOverdueSchedules(LocalDate asOfDate);

    List<ContractPaymentScheduleResponse> getContractSchedules(Long contractId);

    ContractSettlementPreviewResponse previewSettlement(ContractEntity contract, LocalDate asOfDate);

    void enrichContractResponse(ContractResponse response);

    void enrichContractResponses(List<ContractResponse> responses);
}

