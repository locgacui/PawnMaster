package com.pawnmaster.api.modules.contract;

import com.pawnmaster.api.modules.contract.dto.ContractCreateRequest;
import com.pawnmaster.api.modules.contract.dto.ContractRedeemRequest;
import com.pawnmaster.api.modules.contract.dto.ContractResponse;
import com.pawnmaster.api.modules.contract.dto.InterestCalculationResponse;
import com.pawnmaster.api.modules.contract.dto.InterestCollectionRequest;
import com.pawnmaster.api.modules.contract.dto.InterestPaymentResponse;
import com.pawnmaster.api.modules.contract.dto.LiquidationRequest;

import java.time.LocalDate;
import java.util.List;

public interface ContractService {

    // Maker step
    ContractResponse createContract(ContractCreateRequest req, Long staffUserId);

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

    List<InterestPaymentResponse> getContractPayments(Long contractId);
}
