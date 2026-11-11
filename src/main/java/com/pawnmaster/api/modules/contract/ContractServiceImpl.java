package com.pawnmaster.api.modules.contract;

import com.pawnmaster.api.common.BusinessCodeGenerator;
import com.pawnmaster.api.common.BusinessException;
import com.pawnmaster.api.common.ErrorCode;
import com.pawnmaster.api.modules.branch.BranchEntity;
import com.pawnmaster.api.modules.branch.BranchRepository;
import com.pawnmaster.api.modules.cashbook.CashbookService;
import com.pawnmaster.api.modules.cashbook.TransactionCategory;
import com.pawnmaster.api.modules.collateral.CollateralCategory;
import com.pawnmaster.api.modules.collateral.CollateralEntity;
import com.pawnmaster.api.modules.collateral.CollateralRepository;
import com.pawnmaster.api.modules.collateral.CollateralStatus;
import com.pawnmaster.api.modules.collateral.LtvPolicyService;
import com.pawnmaster.api.modules.contract.dto.CollateralCategoryGroupProjection;
import com.pawnmaster.api.modules.contract.dto.ContractCreateRequest;
import com.pawnmaster.api.modules.contract.dto.ContractRedeemRequest;
import com.pawnmaster.api.modules.contract.dto.ContractResponse;
import com.pawnmaster.api.modules.contract.dto.DashboardKpiProjection;
import com.pawnmaster.api.modules.contract.dto.DashboardTabCountsProjection;
import com.pawnmaster.api.modules.contract.dto.InterestCalculationResponse;
import com.pawnmaster.api.modules.contract.dto.InterestCollectionRequest;
import com.pawnmaster.api.modules.contract.dto.InterestPaymentResponse;
import com.pawnmaster.api.modules.contract.dto.LiquidationRequest;
import com.pawnmaster.api.modules.contract.dto.QuickContractOriginationRequest;
import com.pawnmaster.api.modules.customer.CustomerEntity;
import com.pawnmaster.api.modules.customer.CustomerOccupationGroup;
import com.pawnmaster.api.modules.customer.CustomerRepository;
import com.pawnmaster.api.modules.customer.CustomerStatus;
import com.pawnmaster.api.modules.customer.VerificationDocumentType;
import com.pawnmaster.api.modules.dashboard.DashboardContractTab;
import com.pawnmaster.api.modules.dashboard.DashboardPeriod;
import com.pawnmaster.api.modules.dashboard.DashboardTimeframe;
import com.pawnmaster.api.modules.dashboard.dto.DashboardSummaryResponse;
import com.pawnmaster.api.modules.user.RoleEnum;
import com.pawnmaster.api.modules.user.UserEntity;
import com.pawnmaster.api.modules.user.UserRepository;
import com.pawnmaster.api.security.BranchSecurityGuard;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
    private final BranchSecurityGuard branchSecurityGuard;

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
            InterestPaymentRepository interestPaymentRepository,
            @Autowired(required = false) BranchSecurityGuard branchSecurityGuard) {
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
        this.branchSecurityGuard = branchSecurityGuard != null ? branchSecurityGuard : new BranchSecurityGuard();
    }

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
        this(contractRepository, customerRepository, collateralRepository, branchRepository,
                userRepository, cashbookService, ltvPolicyService,
                verificationDocumentPolicy, interestPaymentRepository, new BranchSecurityGuard());
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
                interestPaymentRepository, new BranchSecurityGuard());
    }

    private void enforceBranchAccess(Long targetBranchId, Long actingUserId) {
        if (branchSecurityGuard != null) {
            branchSecurityGuard.validateBranchAccess(targetBranchId);
        }
        if (actingUserId != null && userRepository != null && targetBranchId != null) {
            userRepository.findByIdAndDeletedFalse(actingUserId)
                    .ifPresent(user -> {
                        if (branchSecurityGuard != null) {
                            branchSecurityGuard.validateUserEntityBranchAccess(user, targetBranchId);
                        }
                    });
        }
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

        enforceBranchAccess(branch.getId(), staffUserId);

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
                .contractCode(generateContractCode(branch.getId(), startDate))
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
    public ContractResponse originateFullContract(QuickContractOriginationRequest req, Long staffUserId) {
        if (req == null) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "Dữ liệu khởi tạo hợp đồng không được để trống");
        }

        // Strict branch access check before touching DB
        enforceBranchAccess(req.getBranchId(), staffUserId);

        BranchEntity branch = branchRepository.findByIdAndDeletedFalse(req.getBranchId())
                .orElseThrow(() -> new BusinessException(ErrorCode.BRANCH_NOT_FOUND, "Không tìm thấy chi nhánh"));

        String cleanIdentity = req.getIdentityNumber() != null ? req.getIdentityNumber().trim() : "";
        if (cleanIdentity.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "Số CCCD/CMND khách hàng không được để trống");
        }

        CustomerOccupationGroup occGroup = req.getOccupationGroup() != null
                ? req.getOccupationGroup()
                : CustomerOccupationGroup.OFFICE_WORKER;

        VerificationDocumentType docType = req.getVerificationDocType();
        if (docType == null || !verificationDocumentPolicy.isDocumentValidForGroup(occGroup, docType)) {
            Set<VerificationDocumentType> allowed = verificationDocumentPolicy.getAllowedDocuments(occGroup);
            docType = allowed.stream().findFirst().orElse(VerificationDocumentType.PAYSLIP_BANK_STATEMENT);
        }

        String docUrls = req.getVerificationDocUrls();
        if (docUrls == null || docUrls.isBlank()) {
            docUrls = "https://storage.pawnmaster.vn/docs/verified-" + cleanIdentity + ".jpg";
        }

        final VerificationDocumentType effectiveDocType = docType;
        final String effectiveDocUrls = docUrls;

        // 1. Find existing customer by Identity Number or create a new Customer
        CustomerEntity customer = customerRepository.findByIdentityNumberAndDeletedFalse(cleanIdentity)
                .map(existing -> {
                    if (req.getCustomerFullName() != null && !req.getCustomerFullName().isBlank()) {
                        existing.setFullName(req.getCustomerFullName().trim());
                    }
                    if (req.getPhoneNumber() != null && !req.getPhoneNumber().isBlank()) {
                        existing.setPhoneNumber(req.getPhoneNumber().trim());
                    }
                    if (req.getAddress() != null && !req.getAddress().isBlank()) {
                        existing.setAddress(req.getAddress().trim());
                    }
                    existing.setOccupationGroup(occGroup);
                    if (req.getMonthlyIncome() != null) {
                        existing.setMonthlyIncome(req.getMonthlyIncome());
                    }
                    existing.setVerificationDocType(effectiveDocType);
                    existing.setVerificationDocUrls(effectiveDocUrls);
                    return customerRepository.save(existing);
                })
                .orElseGet(() -> {
                    CustomerEntity newCust = CustomerEntity.builder()
                            .customerCode(BusinessCodeGenerator.generateCustomerCode())
                            .branch(branch)
                            .fullName(req.getCustomerFullName().trim())
                            .identityNumber(cleanIdentity)
                            .phoneNumber(req.getPhoneNumber().trim())
                            .address(req.getAddress() != null ? req.getAddress().trim() : branch.getAddressDetail())
                            .provinceCode(branch.getProvinceCode())
                            .districtCode(branch.getDistrictCode())
                            .occupationGroup(occGroup)
                            .monthlyIncome(req.getMonthlyIncome() != null ? req.getMonthlyIncome()
                                    : new BigDecimal("15000000"))
                            .hasSocialInsurance(occGroup == CustomerOccupationGroup.OFFICE_WORKER
                                    || occGroup == CustomerOccupationGroup.FACTORY_WORKER)
                            .verificationDocType(effectiveDocType)
                            .verificationDocUrls(effectiveDocUrls)
                            .status(CustomerStatus.ACTIVE)
                            .build();
                    return customerRepository.save(newCust);
                });

        // 2. Create Collateral entity
        CollateralCategory category = req.getCollateralCategory() != null
                ? req.getCollateralCategory()
                : CollateralCategory.MOTORBIKE;

        String defaultVaultLocation = switch (category) {
            case MOTORBIKE, CAR -> "Kho Xe " + branch.getBranchCode() + " - Khu A";
            case GOLD_JEWELRY, WATCH -> "Két Sắt " + branch.getBranchCode() + " - Ngăn VIP";
            default -> "Tủ Chống Ẩm " + branch.getBranchCode() + " - Ngăn B";
        };

        CollateralEntity collateral = CollateralEntity.builder()
                .collateralCode(BusinessCodeGenerator.generateCollateralCode(branch.getId(), category))
                .customer(customer)
                .branch(branch)
                .name(req.getCollateralName().trim())
                .category(category)
                .serialOrFrameNumber(req.getSerialOrFrameNumber() != null ? req.getSerialOrFrameNumber().trim() : null)
                .licensePlate(req.getLicensePlate() != null ? req.getLicensePlate().trim() : null)
                .conditionDescription(req.getConditionDescription() != null ? req.getConditionDescription().trim()
                        : "Tài sản nguyên trạng, đã niêm phong")
                .appraisedValue(req.getAppraisedValue())
                .storageLocation(req.getStorageLocation() != null && !req.getStorageLocation().isBlank()
                        ? req.getStorageLocation().trim()
                        : defaultVaultLocation)
                .status(CollateralStatus.IN_VAULT)
                .build();
        CollateralEntity savedCollateral = collateralRepository.save(collateral);

        // 3. Delegate to createContract (enforces LTV ceiling & creates
        // PENDING_APPROVAL contract)
        ContractCreateRequest createReq = ContractCreateRequest.builder()
                .branchId(branch.getId())
                .customerId(customer.getId())
                .collateralId(savedCollateral.getId())
                .principalAmount(req.getPrincipalAmount())
                .interestType(req.getInterestType() != null ? req.getInterestType() : InterestType.DAILY_PER_MILLION)
                .interestRate(req.getInterestRate())
                .storageFeeRate(req.getStorageFeeRate() != null ? req.getStorageFeeRate() : BigDecimal.ZERO)
                .loanTermDays(req.getLoanTermDays() != null ? req.getLoanTermDays() : 30)
                .paymentCycleDays(req.getPaymentCycleDays() != null ? req.getPaymentCycleDays() : 10)
                .startDate(req.getStartDate() != null ? req.getStartDate() : LocalDate.now())
                .notes(req.getNotes())
                .build();

        return createContract(createReq, staffUserId);
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

        Long branchId = contract.getBranch() != null ? contract.getBranch().getId() : null;
        if (branchSecurityGuard != null) {
            branchSecurityGuard.validateBranchAccess(branchId);
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
            collateral.setUpdatedBy(approver.getUsername());
            collateral.setUpdatedAt(LocalDateTime.now());
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

        Long branchId = contract.getBranch() != null ? contract.getBranch().getId() : null;
        if (branchSecurityGuard != null) {
            branchSecurityGuard.validateBranchAccess(branchId);
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
    @Transactional(readOnly = true)
    public InterestCalculationResponse calculateContractInterest(Long contractId, LocalDate fromDate,
            LocalDate toDate) {
        ContractEntity contract = contractRepository.findByIdAndDeletedFalse(contractId)
                .orElseThrow(
                        () -> new BusinessException(ErrorCode.CONTRACT_NOT_FOUND, "Không tìm thấy hợp đồng cầm đồ"));
        Long branchId = contract.getBranch() != null ? contract.getBranch().getId() : null;
        if (branchSecurityGuard != null) {
            branchSecurityGuard.validateBranchAccess(branchId);
        }
        return calculateInterest(contract, fromDate, toDate);
    }

    @Override
    @Transactional
    public InterestPaymentResponse collectInterest(Long contractId, InterestCollectionRequest req, Long userId) {
        ContractEntity contract = contractRepository.findByIdAndDeletedFalse(contractId)
                .orElseThrow(
                        () -> new BusinessException(ErrorCode.CONTRACT_NOT_FOUND, "Không tìm thấy hợp đồng cầm đồ"));

        Long branchId = contract.getBranch() != null ? contract.getBranch().getId() : null;
        enforceBranchAccess(branchId, userId);

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

        Long branchId = contract.getBranch() != null ? contract.getBranch().getId() : null;
        enforceBranchAccess(branchId, userId);

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

        Long branchId = contract.getBranch() != null ? contract.getBranch().getId() : null;
        enforceBranchAccess(branchId, userId);

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

    private static final List<ContractStatus> OPEN_COLLECTIBLE_STATUSES = List.of(
            ContractStatus.ACTIVE,
            ContractStatus.EXTENDED,
            ContractStatus.OVERDUE);

    @Override
    @Transactional(readOnly = true)
    public ContractResponse getContractById(Long id) {
        ContractEntity contract = contractRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(
                        () -> new BusinessException(ErrorCode.CONTRACT_NOT_FOUND, "Không tìm thấy hợp đồng cầm đồ"));
        if (branchSecurityGuard != null && contract.getBranch() != null) {
            branchSecurityGuard.validateBranchAccess(contract.getBranch().getId());
        }
        return ContractResponse.fromEntity(contract);
    }

    @Override
    @Transactional(readOnly = true)
    public ContractResponse getContractByCode(String contractCode) {
        ContractEntity contract = contractRepository.findByContractCodeAndDeletedFalse(contractCode)
                .orElseThrow(
                        () -> new BusinessException(ErrorCode.CONTRACT_NOT_FOUND, "Không tìm thấy hợp đồng cầm đồ"));
        if (branchSecurityGuard != null && contract.getBranch() != null) {
            branchSecurityGuard.validateBranchAccess(contract.getBranch().getId());
        }
        return ContractResponse.fromEntity(contract);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ContractResponse> getAllContracts() {
        if (branchSecurityGuard != null) {
            branchSecurityGuard.validateBranchAccess(null);
        }
        return contractRepository.findByDeletedFalseOrderByCreatedAtDesc().stream()
                .map(ContractResponse::fromEntity)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<ContractResponse> getContractsByBranch(Long branchId) {
        if (branchSecurityGuard != null) {
            branchSecurityGuard.validateBranchAccess(branchId);
        }
        return contractRepository.findByBranchIdAndDeletedFalseOrderByCreatedAtDesc(branchId).stream()
                .map(ContractResponse::fromEntity)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<ContractResponse> getContractsByBranchAndStatus(Long branchId, ContractStatus status) {
        if (branchSecurityGuard != null) {
            branchSecurityGuard.validateBranchAccess(branchId);
        }
        return contractRepository.findByBranchIdAndStatusAndDeletedFalse(branchId, status).stream()
                .map(ContractResponse::fromEntity)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<ContractResponse> getContractsByCustomer(Long customerId) {
        return contractRepository.findByCustomerIdAndDeletedFalse(customerId).stream()
                .map(ContractResponse::fromEntity)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<ContractResponse> getContractsByStatus(ContractStatus status) {
        return contractRepository.findByStatusAndDeletedFalse(status).stream()
                .map(ContractResponse::fromEntity)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<ContractResponse> getDashboardMonthlyContracts(Long branchId, YearMonth targetMonth) {
        Long authorizedBranchId = branchSecurityGuard != null
                ? branchSecurityGuard.resolveAuthorizedBranchId(branchId)
                : branchId;

        YearMonth effectiveMonth = targetMonth != null ? targetMonth : YearMonth.now();
        LocalDate monthStart = effectiveMonth.atDay(1);
        LocalDate monthEnd = effectiveMonth.atEndOfMonth();

        return contractRepository.findDashboardMonthlyContracts(
                authorizedBranchId,
                monthStart,
                monthEnd,
                OPEN_COLLECTIBLE_STATUSES)
                .stream()
                .map(ContractResponse::fromEntity)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public BigDecimal getCollectedInterestForMonth(Long branchId, YearMonth targetMonth) {
        Long authorizedBranchId = branchSecurityGuard != null
                ? branchSecurityGuard.resolveAuthorizedBranchId(branchId)
                : branchId;

        if (interestPaymentRepository == null) {
            return BigDecimal.ZERO;
        }

        YearMonth effectiveMonth = targetMonth != null ? targetMonth : YearMonth.now();
        LocalDateTime fromTime = effectiveMonth.atDay(1).atStartOfDay();
        LocalDateTime toTime = effectiveMonth.atEndOfMonth().plusDays(1).atStartOfDay();

        BigDecimal sum = interestPaymentRepository.sumCollectedInterestInPeriod(authorizedBranchId, fromTime, toTime);
        return sum != null ? sum : BigDecimal.ZERO;
    }

    @Override
    @Transactional(readOnly = true)
    public List<InterestPaymentResponse> getContractPayments(Long contractId) {
        ContractEntity contract = contractRepository.findByIdAndDeletedFalse(contractId).orElse(null);
        if (contract != null && contract.getBranch() != null && branchSecurityGuard != null) {
            branchSecurityGuard.validateBranchAccess(contract.getBranch().getId());
        }
        return interestPaymentRepository.findByContractIdAndDeletedFalseOrderByPaymentDateDesc(contractId).stream()
                .map(InterestPaymentResponse::fromEntity)
                .toList();
    }

    // =========================================================================
    // MILESTONE 2: SERVER-SIDE PAGINATION & AGGREGATE DASHBOARD SERVICE WRAPPERS
    // =========================================================================

    @Override
    @Transactional(readOnly = true)
    public Page<ContractResponse> findDashboardContractPage(
            Long branchId,
            DashboardPeriod period,
            DashboardContractTab tab,
            String keyword,
            Pageable pageable) {
        Long authorizedBranchId = branchSecurityGuard != null
                ? branchSecurityGuard.resolveAuthorizedBranchId(branchId)
                : branchId;

        DashboardPeriod effectivePeriod = period != null ? period : DashboardPeriod.of(DashboardTimeframe.THIS_MONTH);

        LocalDate startDate = effectivePeriod.startDate();
        LocalDate endDate = effectivePeriod.endDate();
        boolean includeCurrentOverdue = effectivePeriod.includeCurrentOverdue();
        String tabCode = tab != null ? tab.name() : "ALL";
        String normalizedKeyword = (keyword != null && !keyword.isBlank()) ? keyword.trim() : null;
        LocalDate today = LocalDate.now();

        return contractRepository.findDashboardContractPage(
                authorizedBranchId,
                startDate,
                endDate,
                includeCurrentOverdue,
                OPEN_COLLECTIBLE_STATUSES,
                tabCode,
                today,
                normalizedKeyword,
                pageable).map(ContractResponse::fromEntity);
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, Long> getDashboardTabCounts(
            Long branchId,
            DashboardPeriod period) {
        Long authorizedBranchId = branchSecurityGuard != null
                ? branchSecurityGuard.resolveAuthorizedBranchId(branchId)
                : branchId;

        DashboardPeriod effectivePeriod = period != null ? period : DashboardPeriod.of(DashboardTimeframe.THIS_MONTH);

        LocalDate startDate = effectivePeriod.startDate();
        LocalDate endDate = effectivePeriod.endDate();
        boolean includeCurrentOverdue = effectivePeriod.includeCurrentOverdue();
        LocalDate today = LocalDate.now();

        DashboardTabCountsProjection proj = contractRepository.countDashboardTabs(
                authorizedBranchId,
                startDate,
                endDate,
                includeCurrentOverdue,
                OPEN_COLLECTIBLE_STATUSES,
                today);

        Map<String, Long> counts = new LinkedHashMap<>();
        counts.put("ALL", proj != null ? proj.getAllCount() : 0L);
        counts.put("PENDING_APPROVAL", proj != null ? proj.getPendingCount() : 0L);
        counts.put("ACTIVE", proj != null ? proj.getActiveCount() : 0L);
        counts.put("DUE_TODAY", proj != null ? proj.getDueTodayCount() : 0L);
        counts.put("OVERDUE", proj != null ? proj.getOverdueCount() : 0L);
        counts.put("WAITING_LIQUIDATION", proj != null ? proj.getWaitingLiquidationCount() : 0L);
        return counts;
    }

    @Override
    @Transactional(readOnly = true)
    public DashboardKpiProjection getPortfolioKpi(Long branchId) {
        Long authorizedBranchId = branchSecurityGuard != null
                ? branchSecurityGuard.resolveAuthorizedBranchId(branchId)
                : branchId;
        return contractRepository.aggregatePortfolioKpis(authorizedBranchId);
    }

    @Override
    @Transactional(readOnly = true)
    public DashboardKpiProjection getPeriodKpi(
            Long branchId,
            DashboardPeriod period) {
        Long authorizedBranchId = branchSecurityGuard != null
                ? branchSecurityGuard.resolveAuthorizedBranchId(branchId)
                : branchId;

        DashboardPeriod effectivePeriod = period != null ? period : DashboardPeriod.of(DashboardTimeframe.THIS_MONTH);

        return contractRepository.aggregatePeriodKpis(
                authorizedBranchId,
                effectivePeriod.startDate(),
                effectivePeriod.endDate(),
                effectivePeriod.includeCurrentOverdue(),
                OPEN_COLLECTIBLE_STATUSES);
    }

    @Override
    @Transactional(readOnly = true)
    public List<DashboardSummaryResponse.CollateralCategoryShare> getDashboardCollateralDistribution(
            Long branchId) {
        Long authorizedBranchId = branchSecurityGuard != null
                ? branchSecurityGuard.resolveAuthorizedBranchId(branchId)
                : branchId;

        List<CollateralCategoryGroupProjection> groups = contractRepository
                .aggregateCollateralDistribution(authorizedBranchId);

        return mapToCollateralShares(groups);
    }

    private List<DashboardSummaryResponse.CollateralCategoryShare> mapToCollateralShares(
            List<CollateralCategoryGroupProjection> groups) {
        long totalCount = groups != null
                ? groups.stream().mapToLong(CollateralCategoryGroupProjection::getContractCount).sum()
                : 0L;

        String[][] targetGroups = new String[][] {
                { "MOTORBIKE", "Xe máy (Honda, Yamaha...)" },
                { "CAR", "Ô tô con / Bán tải" },
                { "GOLD_JEWELRY", "Vàng SJC / Trang sức" },
                { "PHONE_LAPTOP", "Điện thoại / Laptop" },
                { "WATCH_OTHER", "Đồng hồ & Khác" }
        };

        List<DashboardSummaryResponse.CollateralCategoryShare> shares = new ArrayList<>(5);
        for (String[] g : targetGroups) {
            String key = g[0];
            String label = g[1];
            long count = 0L;
            BigDecimal appraised = BigDecimal.ZERO;

            if (groups != null) {
                for (CollateralCategoryGroupProjection item : groups) {
                    if (matchesCollateralGroup(item.getCategory(), key)) {
                        count += item.getContractCount();
                        if (item.getTotalAppraisedValue() != null) {
                            appraised = appraised.add(item.getTotalAppraisedValue());
                        }
                    }
                }
            }

            BigDecimal pct = totalCount > 0
                    ? BigDecimal.valueOf(count * 100.0 / totalCount).setScale(1, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;

            shares.add(DashboardSummaryResponse.CollateralCategoryShare.builder()
                    .groupKey(key)
                    .label(label)
                    .contractCount(count)
                    .percentage(pct)
                    .totalAppraisedValue(appraised)
                    .build());
        }
        return shares;
    }

    private boolean matchesCollateralGroup(CollateralCategory cat, String groupKey) {
        if (cat == null) {
            return "WATCH_OTHER".equals(groupKey);
        }
        return switch (groupKey) {
            case "MOTORBIKE" -> cat == CollateralCategory.MOTORBIKE;
            case "CAR" -> cat == CollateralCategory.CAR;
            case "GOLD_JEWELRY" -> cat == CollateralCategory.GOLD_JEWELRY;
            case "PHONE_LAPTOP" -> cat == CollateralCategory.PHONE
                    || cat == CollateralCategory.LAPTOP;
            case "WATCH_OTHER" -> cat == CollateralCategory.WATCH
                    || cat == CollateralCategory.REAL_ESTATE
                    || cat == CollateralCategory.OTHER;
            default -> false;
        };
    }

    @Override
    @Transactional(readOnly = true)
    public BigDecimal getUpcomingCycleInterest(
            Long branchId,
            DashboardPeriod period) {
        Long authorizedBranchId = branchSecurityGuard != null
                ? branchSecurityGuard.resolveAuthorizedBranchId(branchId)
                : branchId;

        BigDecimal interest = contractRepository.sumUpcomingCycleInterest(authorizedBranchId);
        return interest != null ? interest : BigDecimal.ZERO;
    }

    @Override
    @Transactional(readOnly = true)
    public BigDecimal getCollectedInterestInPeriod(Long branchId, LocalDateTime fromTime, LocalDateTime toTime) {
        Long authorizedBranchId = branchSecurityGuard != null
                ? branchSecurityGuard.resolveAuthorizedBranchId(branchId)
                : branchId;

        if (interestPaymentRepository == null) {
            return BigDecimal.ZERO;
        }

        BigDecimal sum = interestPaymentRepository.sumCollectedInterestInPeriod(authorizedBranchId, fromTime, toTime);
        return sum != null ? sum : BigDecimal.ZERO;
    }

    private String generateContractCode(Long branchId, LocalDate startDate) {
        return BusinessCodeGenerator.generateContractCode(branchId, startDate);
    }

    private String generatePaymentCode(PaymentType paymentType, LocalDateTime paymentDate) {
        return BusinessCodeGenerator.generatePaymentCode(paymentType, paymentDate);
    }
}
