package com.pawnmaster.api.modules.contract;

import com.pawnmaster.api.common.BusinessCodeGenerator;
import com.pawnmaster.api.common.BusinessException;
import com.pawnmaster.api.common.ErrorCode;
import com.pawnmaster.api.modules.branch.BranchEntity;
import com.pawnmaster.api.modules.branch.BranchRepository;
import com.pawnmaster.api.modules.cashbook.CashbookService;
import com.pawnmaster.api.modules.cashbook.TransactionCategory;
import com.pawnmaster.api.modules.collateral.CollateralEntity;
import com.pawnmaster.api.modules.collateral.CollateralRepository;
import com.pawnmaster.api.modules.collateral.CollateralStatus;
import com.pawnmaster.api.modules.collateral.LtvPolicyService;
import com.pawnmaster.api.modules.contract.dto.ContractCreateRequest;
import com.pawnmaster.api.modules.contract.dto.ContractRedeemRequest;
import com.pawnmaster.api.modules.contract.dto.ContractResponse;
import com.pawnmaster.api.modules.contract.dto.InterestCalculationResponse;
import com.pawnmaster.api.modules.contract.dto.InterestCollectionRequest;
import com.pawnmaster.api.modules.contract.dto.InterestPaymentResponse;
import com.pawnmaster.api.modules.contract.dto.LiquidationRequest;
import com.pawnmaster.api.modules.customer.CustomerEntity;
import com.pawnmaster.api.modules.customer.CustomerRepository;
import com.pawnmaster.api.modules.user.RoleEnum;
import com.pawnmaster.api.modules.user.UserEntity;
import com.pawnmaster.api.modules.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Slf4j
@Service
public class ContractServiceImpl implements ContractService {

    private final ContractRepository contractRepository;
    private final CustomerRepository customerRepository;
    private final CollateralRepository collateralRepository;
    private final BranchRepository branchRepository;
    private final UserRepository userRepository;
    private final CashbookService cashbookService;
    private final LtvPolicyService ltvPolicyService;
    private final com.pawnmaster.api.modules.customer.VerificationDocumentPolicy verificationDocumentPolicy;
    private final InterestPaymentRepository interestPaymentRepository;

    @Autowired
    public ContractServiceImpl(
            ContractRepository contractRepository,
            CustomerRepository customerRepository,
            CollateralRepository collateralRepository,
            BranchRepository branchRepository,
            UserRepository userRepository,
            CashbookService cashbookService,
            LtvPolicyService ltvPolicyService,
            com.pawnmaster.api.modules.customer.VerificationDocumentPolicy verificationDocumentPolicy,
            InterestPaymentRepository interestPaymentRepository) {
        this.contractRepository = contractRepository;
        this.customerRepository = customerRepository;
        this.collateralRepository = collateralRepository;
        this.branchRepository = branchRepository;
        this.userRepository = userRepository;
        this.cashbookService = cashbookService;
        this.ltvPolicyService = ltvPolicyService;
        this.verificationDocumentPolicy = verificationDocumentPolicy != null ? verificationDocumentPolicy
                : new com.pawnmaster.api.modules.customer.VerificationDocumentPolicy();
        this.interestPaymentRepository = interestPaymentRepository;
    }

    public ContractServiceImpl(
            ContractRepository contractRepository,
            CustomerRepository customerRepository,
            CollateralRepository collateralRepository,
            BranchRepository branchRepository,
            UserRepository userRepository,
            CashbookService cashbookService,
            LtvPolicyService ltvPolicyService,
            InterestPaymentRepository interestPaymentRepository) {
        this(contractRepository, customerRepository, collateralRepository, branchRepository,
                userRepository, cashbookService, ltvPolicyService,
                new com.pawnmaster.api.modules.customer.VerificationDocumentPolicy(),
                interestPaymentRepository);
    }

    @Override
    @Transactional
    public ContractResponse createContract(ContractCreateRequest req, Long staffUserId) {
        if (req == null) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "Yêu cầu tạo hợp đồng không được để trống");
        }

        CustomerEntity customer = customerRepository.findByIdAndDeletedFalse(req.getCustomerId())
                .orElseThrow(() -> new BusinessException(ErrorCode.CUSTOMER_NOT_FOUND, "Không tìm thấy khách hàng"));

        CollateralEntity collateral = collateralRepository.findByIdAndDeletedFalse(req.getCollateralId())
                .orElseThrow(() -> new BusinessException(ErrorCode.COLLATERAL_NOT_FOUND, "Không tìm thấy tài sản"));

        BranchEntity branch;
        if (req.getBranchId() != null) {
            branch = branchRepository.findByIdAndDeletedFalse(req.getBranchId())
                    .orElseThrow(() -> new BusinessException(ErrorCode.BRANCH_NOT_FOUND, "Không tìm thấy chi nhánh"));
        } else if (collateral.getBranch() != null) {
            branch = collateral.getBranch();
        } else if (customer.getBranch() != null) {
            branch = customer.getBranch();
        } else {
            throw new BusinessException(ErrorCode.BRANCH_NOT_FOUND, "Chi nhánh không hợp lệ");
        }

        // Validate via Verification Document Policy & LTV Policy
        if (verificationDocumentPolicy != null) {
            verificationDocumentPolicy.validateVerificationDocuments(
                    customer.getOccupationGroup(),
                    customer.getVerificationDocType(),
                    customer.getVerificationDocUrlList());
        }

        ltvPolicyService.validateLoanCeiling(
                customer.getOccupationGroup(),
                collateral.getCategory(),
                collateral.getAppraisedValue(),
                req.getPrincipalAmount());

        BigDecimal appliedLtv = req.getPrincipalAmount()
                .multiply(BigDecimal.valueOf(100))
                .divide(collateral.getAppraisedValue(), 2, RoundingMode.HALF_UP);

        LocalDate startDate = req.getStartDate() != null ? req.getStartDate() : LocalDate.now();
        int loanTermDays = req.getLoanTermDays() != null ? req.getLoanTermDays() : 30;
        int paymentCycleDays = req.getPaymentCycleDays() != null ? req.getPaymentCycleDays() : 10;

        LocalDate maturityDate = startDate.plusDays(loanTermDays);
        LocalDate nextInterestDueDate = startDate.plusDays(paymentCycleDays);

        ContractEntity contract = ContractEntity.builder()
                .contractCode(generateContractCode(branch != null ? branch.getId() : req.getBranchId(), startDate))
                .customer(customer)
                .collateral(collateral)
                .branch(branch)
                .principalAmount(req.getPrincipalAmount())
                .remainingPrincipal(req.getPrincipalAmount())
                .appliedLtvPercent(appliedLtv)
                .interestType(req.getInterestType() != null ? req.getInterestType() : InterestType.DAILY_PER_MILLION)
                .interestRate(req.getInterestRate())
                .storageFeeRate(req.getStorageFeeRate() != null ? req.getStorageFeeRate() : BigDecimal.ZERO)
                .loanTermDays(loanTermDays)
                .paymentCycleDays(paymentCycleDays)
                .startDate(startDate)
                .maturityDate(maturityDate)
                .nextInterestDueDate(nextInterestDueDate)
                .totalInterestPaid(BigDecimal.ZERO)
                .status(ContractStatus.PENDING_APPROVAL)
                .notes(req.getNotes())
                .build();

        ContractEntity saved = contractRepository.save(contract);
        log.info("Contract created in PENDING_APPROVAL: code={}, branch={}", saved.getContractCode(),
                branch.getBranchCode());
        return ContractResponse.fromEntity(saved);
    }

    @Override
    @Transactional
    public ContractResponse approveAndDisburseContract(Long contractId, Long managerUserId, String notes) {
        UserEntity approver = userRepository.findByIdAndDeletedFalse(managerUserId)
                .orElseThrow(
                        () -> new BusinessException(ErrorCode.USER_NOT_FOUND, "Không tìm thấy người dùng phê duyệt"));

        ContractEntity contract = contractRepository.findByIdAndDeletedFalse(contractId)
                .orElseThrow(
                        () -> new BusinessException(ErrorCode.CONTRACT_NOT_FOUND, "Không tìm thấy hợp đồng cầm đồ"));

        if (contract.getStatus() != ContractStatus.PENDING_APPROVAL) {
            throw new BusinessException(ErrorCode.CONTRACT_NOT_PENDING_APPROVAL,
                    "Chỉ có thể phê duyệt hợp đồng đang ở trạng thái Chờ duyệt");
        }

        // Strict RBAC & Branch authorization check
        boolean isAdmin = approver.getRole() == RoleEnum.ADMIN;
        boolean isBranchManager = approver.getRole() == RoleEnum.MANAGER
                && approver.getBranch() != null
                && contract.getBranch() != null
                && approver.getBranch().getId().equals(contract.getBranch().getId());

        if (!isAdmin && !isBranchManager) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "Bạn không có quyền thực hiện thao tác này");
        }

        // Disburse loan via Cashbook (checks branch balance, throws
        // INSUFFICIENT_CASH_BALANCE if insufficient)
        Long branchId = contract.getBranch() != null ? contract.getBranch().getId() : null;
        cashbookService.disburseLoan(
                branchId,
                contract.getPrincipalAmount(),
                contract.getId(),
                managerUserId,
                notes != null ? notes : ("Giải ngân hợp đồng " + contract.getContractCode()));

        // Transition contract state to ACTIVE
        contract.setStatus(ContractStatus.ACTIVE);
        contract.setApprovedBy(managerUserId);
        contract.setApprovedAt(LocalDateTime.now());
        contract.setApprovalNotes(notes);
        contractRepository.save(contract);

        // Put collateral into vault
        CollateralEntity collateral = contract.getCollateral();
        if (collateral != null) {
            collateral.setStatus(CollateralStatus.IN_VAULT);
            collateralRepository.save(collateral);
        }

        log.info("Contract approved and disbursed: id={}, code={}, approver={}", contract.getId(),
                contract.getContractCode(), approver.getUsername());
        return ContractResponse.fromEntity(contract);
    }

    @Override
    @Transactional
    public ContractResponse rejectContract(Long contractId, Long managerUserId, String reason) {
        UserEntity approver = userRepository.findByIdAndDeletedFalse(managerUserId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND, "Không tìm thấy người dùng"));

        ContractEntity contract = contractRepository.findByIdAndDeletedFalse(contractId)
                .orElseThrow(
                        () -> new BusinessException(ErrorCode.CONTRACT_NOT_FOUND, "Không tìm thấy hợp đồng cầm đồ"));

        if (contract.getStatus() != ContractStatus.PENDING_APPROVAL) {
            throw new BusinessException(ErrorCode.CONTRACT_NOT_PENDING_APPROVAL,
                    "Chỉ có thể từ chối hợp đồng đang ở trạng thái Chờ duyệt");
        }

        boolean isAdmin = approver.getRole() == RoleEnum.ADMIN;
        boolean isBranchManager = approver.getRole() == RoleEnum.MANAGER
                && approver.getBranch() != null
                && contract.getBranch() != null
                && approver.getBranch().getId().equals(contract.getBranch().getId());

        if (!isAdmin && !isBranchManager) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "Bạn không có quyền thực hiện thao tác này");
        }

        contract.setStatus(ContractStatus.CANCELLED);
        contract.setApprovalNotes(reason);
        contract.setClosedDate(LocalDate.now());
        contractRepository.save(contract);

        log.info("Contract rejected: id={}, code={}, reason={}", contract.getId(), contract.getContractCode(), reason);
        return ContractResponse.fromEntity(contract);
    }

    @Override
    public InterestCalculationResponse calculateInterest(ContractEntity contract, LocalDate fromDate,
            LocalDate toDate) {
        if (contract == null || fromDate == null || toDate == null) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "Dữ liệu tính lãi không hợp lệ");
        }

        long days = ChronoUnit.DAYS.between(fromDate, toDate);
        if (days < 0) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "Khoảng thời gian tính lãi không hợp lệ");
        }

        BigDecimal principal = contract.getRemainingPrincipal() != null ? contract.getRemainingPrincipal()
                : contract.getPrincipalAmount();
        BigDecimal rate = contract.getInterestRate() != null ? contract.getInterestRate() : BigDecimal.ZERO;
        BigDecimal storageRate = contract.getStorageFeeRate() != null ? contract.getStorageFeeRate() : BigDecimal.ZERO;

        BigDecimal interestAmount = BigDecimal.ZERO;
        BigDecimal storageFeeAmount = BigDecimal.ZERO;

        InterestType type = contract.getInterestType() != null ? contract.getInterestType()
                : InterestType.DAILY_PER_MILLION;

        switch (type) {
            case DAILY_PER_MILLION: {
                BigDecimal oneMillion = new BigDecimal("1000000");
                BigDecimal factor = principal.divide(oneMillion, 8, RoundingMode.HALF_UP);
                interestAmount = factor.multiply(rate).multiply(BigDecimal.valueOf(days)).setScale(2,
                        RoundingMode.HALF_UP);
                storageFeeAmount = factor.multiply(storageRate).multiply(BigDecimal.valueOf(days)).setScale(2,
                        RoundingMode.HALF_UP);
                break;
            }
            case PERCENT_MONTHLY: {
                BigDecimal rateFactor = rate.divide(new BigDecimal("100"), 8, RoundingMode.HALF_UP);
                BigDecimal dayFactor = BigDecimal.valueOf(days).divide(new BigDecimal("30"), 8, RoundingMode.HALF_UP);
                interestAmount = principal.multiply(rateFactor).multiply(dayFactor).setScale(2, RoundingMode.HALF_UP);

                if (storageRate.compareTo(BigDecimal.ZERO) > 0) {
                    BigDecimal storageFactor = storageRate.divide(new BigDecimal("100"), 8, RoundingMode.HALF_UP);
                    storageFeeAmount = principal.multiply(storageFactor).multiply(dayFactor).setScale(2,
                            RoundingMode.HALF_UP);
                }
                break;
            }
            case PERCENT_WEEKLY: {
                BigDecimal rateFactor = rate.divide(new BigDecimal("100"), 8, RoundingMode.HALF_UP);
                BigDecimal dayFactor = BigDecimal.valueOf(days).divide(new BigDecimal("7"), 8, RoundingMode.HALF_UP);
                interestAmount = principal.multiply(rateFactor).multiply(dayFactor).setScale(2, RoundingMode.HALF_UP);

                if (storageRate.compareTo(BigDecimal.ZERO) > 0) {
                    BigDecimal storageFactor = storageRate.divide(new BigDecimal("100"), 8, RoundingMode.HALF_UP);
                    storageFeeAmount = principal.multiply(storageFactor).multiply(dayFactor).setScale(2,
                            RoundingMode.HALF_UP);
                }
                break;
            }
        }

        BigDecimal totalDue = interestAmount.add(storageFeeAmount);

        return InterestCalculationResponse.builder()
                .contractId(contract.getId())
                .contractCode(contract.getContractCode())
                .principalAmount(principal)
                .remainingPrincipal(contract.getRemainingPrincipal())
                .interestType(type)
                .interestRate(rate)
                .storageFeeRate(storageRate)
                .fromDate(fromDate)
                .toDate(toDate)
                .daysCount((int) days)
                .interestAmount(interestAmount)
                .storageFeeAmount(storageFeeAmount)
                .penaltyAmount(BigDecimal.ZERO)
                .totalDue(totalDue)
                .build();
    }

    @Override
    public InterestCalculationResponse calculateContractInterest(Long contractId, LocalDate fromDate,
            LocalDate toDate) {
        ContractEntity contract = contractRepository.findByIdAndDeletedFalse(contractId)
                .orElseThrow(
                        () -> new BusinessException(ErrorCode.CONTRACT_NOT_FOUND, "Không tìm thấy hợp đồng cầm đồ"));
        return calculateInterest(contract, fromDate, toDate);
    }

    @Override
    @Transactional
    public InterestPaymentResponse collectInterest(Long contractId, InterestCollectionRequest req, Long userId) {
        ContractEntity contract = contractRepository.findByIdAndDeletedFalse(contractId)
                .orElseThrow(
                        () -> new BusinessException(ErrorCode.CONTRACT_NOT_FOUND, "Không tìm thấy hợp đồng cầm đồ"));

        if (contract.getStatus() == ContractStatus.REDEEMED
                || contract.getStatus() == ContractStatus.LIQUIDATED
                || contract.getStatus() == ContractStatus.CANCELLED) {
            throw new BusinessException(ErrorCode.CONTRACT_ALREADY_CLOSED, "Hợp đồng đã tất toán hoặc thanh lý");
        }

        BigDecimal principalPaid = req.getPrincipalPaidAmount() != null ? req.getPrincipalPaidAmount()
                : BigDecimal.ZERO;
        if (principalPaid.compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "Số tiền trả gốc không hợp lệ");
        }
        if (principalPaid.compareTo(contract.getRemainingPrincipal()) > 0) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST,
                    "Số tiền trả bớt gốc không được vượt quá dư nợ gốc hiện tại");
        }

        BigDecimal interest = req.getInterestAmount() != null ? req.getInterestAmount() : BigDecimal.ZERO;
        BigDecimal storage = req.getStorageFeeAmount() != null ? req.getStorageFeeAmount() : BigDecimal.ZERO;
        BigDecimal penalty = req.getPenaltyAmount() != null ? req.getPenaltyAmount() : BigDecimal.ZERO;

        BigDecimal totalInterestCost = interest.add(storage).add(penalty);
        BigDecimal totalPayment = totalInterestCost.add(principalPaid);

        // Update contract metrics
        contract.setTotalInterestPaid(contract.getTotalInterestPaid().add(totalInterestCost));
        if (principalPaid.compareTo(BigDecimal.ZERO) > 0) {
            contract.setRemainingPrincipal(contract.getRemainingPrincipal().subtract(principalPaid));
        }

        int cycleDays = contract.getPaymentCycleDays() != null ? contract.getPaymentCycleDays() : 10;
        contract.setNextInterestDueDate(contract.getNextInterestDueDate().plusDays(cycleDays));

        if (contract.getStatus() == ContractStatus.OVERDUE) {
            contract.setStatus(ContractStatus.ACTIVE);
            if (contract.getCollateral() != null) {
                contract.getCollateral().setStatus(CollateralStatus.IN_VAULT);
                collateralRepository.save(contract.getCollateral());
            }
        }
        contractRepository.save(contract);

        // Record InterestPaymentEntity
        PaymentType paymentType = principalPaid.compareTo(BigDecimal.ZERO) > 0
                ? PaymentType.PARTIAL_PRINCIPAL
                : PaymentType.INTEREST_PAYMENT;

        LocalDateTime paymentTimestamp = LocalDateTime.now();
        InterestPaymentEntity payment = InterestPaymentEntity.builder()
                .paymentCode(generatePaymentCode(paymentType, paymentTimestamp))
                .contract(contract)
                .paymentType(paymentType)
                .interestAmount(interest)
                .storageFeeAmount(storage)
                .penaltyAmount(penalty)
                .principalPaidAmount(principalPaid)
                .totalAmount(totalPayment)
                .paymentDate(paymentTimestamp)
                .paymentMethod(req.getPaymentMethod() != null ? req.getPaymentMethod() : "CASH")
                .notes(req.getNotes())
                .build();

        InterestPaymentEntity savedPayment = interestPaymentRepository.save(payment);

        // Record CASH_IN to branch cashbook
        Long branchId = contract.getBranch() != null ? contract.getBranch().getId() : null;
        TransactionCategory category = principalPaid.compareTo(BigDecimal.ZERO) > 0
                && totalInterestCost.compareTo(BigDecimal.ZERO) == 0
                        ? TransactionCategory.PRINCIPAL_COLLECTION
                        : TransactionCategory.INTEREST_COLLECTION;

        cashbookService.collectCashIn(
                branchId,
                totalPayment,
                category,
                contract.getId(),
                userId,
                req.getNotes() != null ? req.getNotes() : ("Thu tiền hợp đồng " + contract.getContractCode()));

        return InterestPaymentResponse.fromEntity(savedPayment);
    }

    @Override
    @Transactional
    public ContractResponse redeemContract(Long contractId, Long userId, String notes) {
        ContractEntity contract = contractRepository.findByIdAndDeletedFalse(contractId)
                .orElseThrow(
                        () -> new BusinessException(ErrorCode.CONTRACT_NOT_FOUND, "Không tìm thấy hợp đồng cầm đồ"));

        if (contract.getStatus() == ContractStatus.REDEEMED
                || contract.getStatus() == ContractStatus.LIQUIDATED
                || contract.getStatus() == ContractStatus.CANCELLED) {
            throw new BusinessException(ErrorCode.CONTRACT_ALREADY_CLOSED, "Hợp đồng đã tất toán hoặc thanh lý");
        }

        BigDecimal remainingPrincipal = contract.getRemainingPrincipal();

        contract.setRemainingPrincipal(BigDecimal.ZERO);
        contract.setStatus(ContractStatus.REDEEMED);
        contract.setClosedDate(LocalDate.now());
        if (notes != null) {
            contract.setNotes(contract.getNotes() != null ? contract.getNotes() + " | " + notes : notes);
        }
        contractRepository.save(contract);

        CollateralEntity collateral = contract.getCollateral();
        if (collateral != null) {
            collateral.setStatus(CollateralStatus.REDEEMED);
            collateralRepository.save(collateral);
        }

        Long branchId = contract.getBranch() != null ? contract.getBranch().getId() : null;
        cashbookService.collectCashIn(
                branchId,
                remainingPrincipal,
                TransactionCategory.PRINCIPAL_COLLECTION,
                contract.getId(),
                userId,
                notes != null ? notes : ("Tất toán chuộc đồ hợp đồng " + contract.getContractCode()));

        return ContractResponse.fromEntity(contract);
    }

    @Override
    @Transactional
    public ContractResponse redeemContract(Long contractId, ContractRedeemRequest req, Long userId) {
        return redeemContract(contractId, userId, req != null ? req.getNotes() : null);
    }

    @Override
    @Transactional
    public ContractResponse liquidateContract(Long contractId, LiquidationRequest req, Long userId) {
        if (req == null || req.getLiquidationAmount() == null
                || req.getLiquidationAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "Số tiền thanh lý không hợp lệ");
        }

        ContractEntity contract = contractRepository.findByIdAndDeletedFalse(contractId)
                .orElseThrow(
                        () -> new BusinessException(ErrorCode.CONTRACT_NOT_FOUND, "Không tìm thấy hợp đồng cầm đồ"));

        if (contract.getStatus() == ContractStatus.REDEEMED
                || contract.getStatus() == ContractStatus.LIQUIDATED
                || contract.getStatus() == ContractStatus.CANCELLED) {
            throw new BusinessException(ErrorCode.CONTRACT_ALREADY_CLOSED, "Hợp đồng đã tất toán hoặc thanh lý");
        }

        contract.setStatus(ContractStatus.LIQUIDATED);
        contract.setRemainingPrincipal(BigDecimal.ZERO);
        contract.setClosedDate(LocalDate.now());
        if (req.getNotes() != null) {
            contract.setNotes(
                    contract.getNotes() != null ? contract.getNotes() + " | " + req.getNotes() : req.getNotes());
        }
        contractRepository.save(contract);

        CollateralEntity collateral = contract.getCollateral();
        if (collateral != null) {
            collateral.setStatus(CollateralStatus.LIQUIDATED);
            collateralRepository.save(collateral);
        }

        Long branchId = contract.getBranch() != null ? contract.getBranch().getId() : null;
        cashbookService.collectCashIn(
                branchId,
                req.getLiquidationAmount(),
                TransactionCategory.LIQUIDATION_INCOME,
                contract.getId(),
                userId,
                req.getNotes() != null ? req.getNotes() : ("Thanh lý tài sản hợp đồng " + contract.getContractCode()));

        return ContractResponse.fromEntity(contract);
    }

    @Override
    @Transactional
    public int markOverdueContracts() {
        return markOverdueContracts(LocalDate.now());
    }

    @Override
    @Transactional
    public int markOverdueContracts(LocalDate asOfDate) {
        List<ContractEntity> activeContracts = contractRepository.findByStatusAndDeletedFalse(ContractStatus.ACTIVE);
        int count = 0;

        for (ContractEntity contract : activeContracts) {
            if (contract.getMaturityDate() != null && asOfDate.isAfter(contract.getMaturityDate())) {
                contract.setStatus(ContractStatus.OVERDUE);
                if (contract.getCollateral() != null) {
                    contract.getCollateral().setStatus(CollateralStatus.WAITING_LIQUIDATION);
                    collateralRepository.save(contract.getCollateral());
                }
                contractRepository.save(contract);
                count++;
            }
        }

        return count;
    }

    @Override
    public ContractResponse getContractById(Long id) {
        ContractEntity contract = contractRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(
                        () -> new BusinessException(ErrorCode.CONTRACT_NOT_FOUND, "Không tìm thấy hợp đồng cầm đồ"));
        return ContractResponse.fromEntity(contract);
    }

    @Override
    public ContractResponse getContractByCode(String contractCode) {
        ContractEntity contract = contractRepository.findByContractCodeAndDeletedFalse(contractCode)
                .orElseThrow(
                        () -> new BusinessException(ErrorCode.CONTRACT_NOT_FOUND, "Không tìm thấy hợp đồng cầm đồ"));
        return ContractResponse.fromEntity(contract);
    }

    @Override
    public List<ContractResponse> getAllContracts() {
        return contractRepository.findByDeletedFalseOrderByCreatedAtDesc().stream()
                .map(ContractResponse::fromEntity)
                .toList();
    }

    @Override
    public List<ContractResponse> getContractsByBranch(Long branchId) {
        return contractRepository.findByBranchIdAndDeletedFalseOrderByCreatedAtDesc(branchId).stream()
                .map(ContractResponse::fromEntity)
                .toList();
    }

    @Override
    public List<ContractResponse> getContractsByBranchAndStatus(Long branchId, ContractStatus status) {
        return contractRepository.findByBranchIdAndStatusAndDeletedFalse(branchId, status).stream()
                .map(ContractResponse::fromEntity)
                .toList();
    }

    @Override
    public List<ContractResponse> getContractsByCustomer(Long customerId) {
        return contractRepository.findByCustomerIdAndDeletedFalse(customerId).stream()
                .map(ContractResponse::fromEntity)
                .toList();
    }

    @Override
    public List<ContractResponse> getContractsByStatus(ContractStatus status) {
        return contractRepository.findByStatusAndDeletedFalse(status).stream()
                .map(ContractResponse::fromEntity)
                .toList();
    }

    @Override
    public List<InterestPaymentResponse> getContractPayments(Long contractId) {
        return interestPaymentRepository.findByContractIdAndDeletedFalseOrderByPaymentDateDesc(contractId).stream()
                .map(InterestPaymentResponse::fromEntity)
                .toList();
    }

    private String generateContractCode(Long branchId, LocalDate startDate) {
        return BusinessCodeGenerator.generateContractCode(branchId, startDate);
    }

    private String generatePaymentCode(PaymentType paymentType, LocalDateTime paymentDate) {
        return BusinessCodeGenerator.generatePaymentCode(paymentType, paymentDate);
    }
}
