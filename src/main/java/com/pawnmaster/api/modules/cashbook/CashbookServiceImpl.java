package com.pawnmaster.api.modules.cashbook;

import com.pawnmaster.api.common.BusinessCodeGenerator;
import com.pawnmaster.api.common.BusinessException;
import com.pawnmaster.api.common.ErrorCode;
import com.pawnmaster.api.modules.branch.BranchEntity;
import com.pawnmaster.api.modules.branch.BranchRepository;
import com.pawnmaster.api.modules.cashbook.dto.CashFlowSummaryResponse;
import com.pawnmaster.api.modules.cashbook.dto.CashbookCreateRequest;
import com.pawnmaster.api.modules.cashbook.dto.CashbookResponse;
import com.pawnmaster.api.modules.contract.ContractEntity;
import com.pawnmaster.api.modules.contract.ContractRepository;
import com.pawnmaster.api.modules.customer.CustomerEntity;
import com.pawnmaster.api.modules.customer.CustomerRepository;
import com.pawnmaster.api.security.BranchSecurityGuard;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class CashbookServiceImpl implements CashbookService {

    private final CashbookRepository cashbookRepository;
    private final BranchRepository branchRepository;
    private final ContractRepository contractRepository;
    private final CustomerRepository customerRepository;

    @Autowired(required = false)
    private BranchSecurityGuard branchSecurityGuard = new BranchSecurityGuard();

    @Override
    @Transactional
    public CashbookResponse collectCashIn(Long branchId, BigDecimal amount, TransactionCategory category,
            Long contractId, Long userId, String notes) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "Số tiền giao dịch phải lớn hơn 0");
        }

        BranchEntity branch = branchRepository.findByIdAndDeletedFalse(branchId)
                .orElseThrow(() -> new BusinessException(ErrorCode.BRANCH_NOT_FOUND, "Không tìm thấy chi nhánh"));

        BigDecimal newBalance = branch.getCurrentCashBalance().add(amount);
        branch.setCurrentCashBalance(newBalance);
        branchRepository.save(branch);

        ContractEntity contract = null;
        CustomerEntity customer = null;
        String payerName = null;

        if (contractId != null && contractRepository != null) {
            contract = contractRepository.findByIdAndDeletedFalse(contractId).orElse(null);
            if (contract != null && contract.getCustomer() != null) {
                customer = contract.getCustomer();
                payerName = customer.getFullName();
            }
        }

        LocalDateTime txTime = LocalDateTime.now();
        CashbookEntity entity = CashbookEntity.builder()
                .transactionCode(generateTransactionCode(TransactionType.CASH_IN, txTime))
                .branch(branch)
                .transactionType(TransactionType.CASH_IN)
                .category(category)
                .amount(amount)
                .balanceAfter(newBalance)
                .paymentMethod("CASH")
                .transactionDate(txTime)
                .contract(contract)
                .customer(customer)
                .payerOrReceiverName(payerName)
                .description(notes)
                .notes(notes)
                .performedBy(userId)
                .build();

        CashbookEntity saved = cashbookRepository.save(entity);
        return CashbookResponse.fromEntity(saved);
    }

    @Override
    @Transactional
    public CashbookResponse collectCashIn(Long branchId, BigDecimal amount, String category,
            Long contractId, Long userId, String notes) {
        TransactionCategory cat;
        try {
            cat = TransactionCategory.valueOf(category);
        } catch (Exception e) {
            cat = TransactionCategory.OTHER;
        }
        return collectCashIn(branchId, amount, cat, contractId, userId, notes);
    }

    @Override
    @Transactional
    public CashbookResponse disburseLoan(Long branchId, BigDecimal amount, Long contractId,
            Long userId, String notes) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "Số tiền giải ngân phải lớn hơn 0");
        }

        BranchEntity branch = branchRepository.findByIdAndDeletedFalse(branchId)
                .orElseThrow(() -> new BusinessException(ErrorCode.BRANCH_NOT_FOUND, "Không tìm thấy chi nhánh"));

        if (branch.getCurrentCashBalance().compareTo(amount) < 0) {
            throw new BusinessException(ErrorCode.INSUFFICIENT_CASH_BALANCE, "Số dư quỹ tiền mặt không đủ");
        }

        BigDecimal newBalance = branch.getCurrentCashBalance().subtract(amount);
        branch.setCurrentCashBalance(newBalance);
        branchRepository.save(branch);

        ContractEntity contract = null;
        CustomerEntity customer = null;
        String receiverName = null;

        if (contractId != null && contractRepository != null) {
            contract = contractRepository.findByIdAndDeletedFalse(contractId).orElse(null);
            if (contract != null && contract.getCustomer() != null) {
                customer = contract.getCustomer();
                receiverName = customer.getFullName();
            }
        }

        String paymentMethod = (contract != null && contract.getDisbursementMethod() != null)
                ? contract.getDisbursementMethod().name()
                : "CASH";
        String effectiveDescription = resolveDisbursementDescription(contract, paymentMethod, notes);

        LocalDateTime txTime = LocalDateTime.now();
        CashbookEntity entity = CashbookEntity.builder()
                .transactionCode(generateTransactionCode(TransactionType.CASH_OUT, txTime))
                .branch(branch)
                .transactionType(TransactionType.CASH_OUT)
                .category(TransactionCategory.DISBURSEMENT)
                .amount(amount)
                .balanceAfter(newBalance)
                .paymentMethod(paymentMethod)
                .transactionDate(txTime)
                .contract(contract)
                .customer(customer)
                .payerOrReceiverName(receiverName)
                .description(effectiveDescription)
                .notes(notes)
                .performedBy(userId)
                .build();

        CashbookEntity saved = cashbookRepository.save(entity);
        return CashbookResponse.fromEntity(saved);
    }

    private String resolveDisbursementDescription(ContractEntity contract, String paymentMethod, String notes) {
        if (!"BANK_TRANSFER".equals(paymentMethod) || contract == null || contract.getDisbursementAccountNumber() == null) {
            return notes;
        }
        String bankDisplay = (contract.getDisbursementBankName() != null && !contract.getDisbursementBankName().isBlank())
                ? contract.getDisbursementBankName()
                : (contract.getDisbursementBankCode() != null ? contract.getDisbursementBankCode() : "NH");
        String holderPart = (contract.getDisbursementAccountHolder() != null && !contract.getDisbursementAccountHolder().isBlank())
                ? " - " + contract.getDisbursementAccountHolder()
                : "";
        String bankTag = String.format("[CK: %s - STK %s%s]", bankDisplay, contract.getDisbursementAccountNumber(), holderPart);

        String combined;
        if (notes == null || notes.isBlank()) {
            combined = bankTag;
        } else if (notes.contains(contract.getDisbursementAccountNumber())) {
            combined = notes;
        } else {
            combined = notes + " " + bankTag;
        }
        return combined.length() > 500 ? combined.substring(0, 500) : combined;
    }

    @Override
    @Transactional
    public CashbookResponse recordTransaction(CashbookCreateRequest request) {
        if (request.getAmount() == null || request.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "Số tiền giao dịch phải lớn hơn 0");
        }

        Long branchId = request.getBranchId();
        if (branchId == null) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "Vui lòng chọn chi nhánh");
        }

        BranchEntity branch = branchRepository.findByIdAndDeletedFalse(branchId)
                .orElseThrow(() -> new BusinessException(ErrorCode.BRANCH_NOT_FOUND, "Không tìm thấy chi nhánh"));

        BigDecimal amount = request.getAmount();
        BigDecimal newBalance;

        if (request.getTransactionType() == TransactionType.CASH_OUT) {
            if (branch.getCurrentCashBalance().compareTo(amount) < 0) {
                throw new BusinessException(ErrorCode.INSUFFICIENT_CASH_BALANCE, "Số dư quỹ tiền mặt không đủ");
            }
            newBalance = branch.getCurrentCashBalance().subtract(amount);
        } else {
            newBalance = branch.getCurrentCashBalance().add(amount);
        }

        branch.setCurrentCashBalance(newBalance);
        branchRepository.save(branch);

        ContractEntity contract = null;
        if (request.getContractId() != null && contractRepository != null) {
            contract = contractRepository.findByIdAndDeletedFalse(request.getContractId()).orElse(null);
        }

        CustomerEntity customer = null;
        if (request.getCustomerId() != null && customerRepository != null) {
            customer = customerRepository.findByIdAndDeletedFalse(request.getCustomerId()).orElse(null);
        } else if (contract != null && contract.getCustomer() != null) {
            customer = contract.getCustomer();
        }

        String noteVal = request.getNotes() != null ? request.getNotes() : request.getDescription();

        LocalDateTime txTime = request.getTransactionDate() != null ? request.getTransactionDate()
                : LocalDateTime.now();
        CashbookEntity entity = CashbookEntity.builder()
                .transactionCode(generateTransactionCode(request.getTransactionType(), txTime))
                .branch(branch)
                .transactionType(request.getTransactionType())
                .category(request.getCategory())
                .amount(amount)
                .balanceAfter(newBalance)
                .paymentMethod(request.getPaymentMethod() != null ? request.getPaymentMethod() : "CASH")
                .transactionDate(txTime)
                .contract(contract)
                .customer(customer)
                .payerOrReceiverName(request.getPayerOrReceiverName())
                .description(request.getDescription())
                .notes(noteVal)
                .performedBy(request.getPerformedBy())
                .build();

        CashbookEntity saved = cashbookRepository.save(entity);
        return CashbookResponse.fromEntity(saved);
    }

    @Override
    @Transactional
    public CashbookResponse recordCashIn(Long branchId, BigDecimal amount, TransactionCategory category,
            Long contractId, Long customerId, String payerName,
            String description, String paymentMethod, Long userId) {
        CashbookCreateRequest req = CashbookCreateRequest.builder()
                .branchId(branchId)
                .transactionType(TransactionType.CASH_IN)
                .category(category)
                .amount(amount)
                .contractId(contractId)
                .customerId(customerId)
                .payerOrReceiverName(payerName)
                .description(description)
                .notes(description)
                .paymentMethod(paymentMethod != null ? paymentMethod : "CASH")
                .performedBy(userId)
                .transactionDate(LocalDateTime.now())
                .build();
        return recordTransaction(req);
    }

    @Override
    @Transactional
    public CashbookResponse recordCashOut(Long branchId, BigDecimal amount, TransactionCategory category,
            Long contractId, Long customerId, String receiverName,
            String description, String paymentMethod, Long userId) {
        CashbookCreateRequest req = CashbookCreateRequest.builder()
                .branchId(branchId)
                .transactionType(TransactionType.CASH_OUT)
                .category(category)
                .amount(amount)
                .contractId(contractId)
                .customerId(customerId)
                .payerOrReceiverName(receiverName)
                .description(description)
                .notes(description)
                .paymentMethod(paymentMethod != null ? paymentMethod : "CASH")
                .performedBy(userId)
                .transactionDate(LocalDateTime.now())
                .build();
        return recordTransaction(req);
    }

    @Override
    @Transactional(readOnly = true)
    public CashbookResponse getTransactionById(Long id) {
        CashbookEntity entity = cashbookRepository.findById(id)
                .filter(e -> !e.isDeleted())
                .orElseThrow(() -> new BusinessException(ErrorCode.CASHBOOK_ENTRY_NOT_FOUND,
                        "Không tìm thấy phiếu thu/chi"));
        return CashbookResponse.fromEntity(entity);
    }

    @Override
    @Transactional(readOnly = true)
    public CashbookResponse getTransactionByCode(String code) {
        CashbookEntity entity = cashbookRepository.findByTransactionCodeAndDeletedFalse(code)
                .orElseThrow(() -> new BusinessException(ErrorCode.CASHBOOK_ENTRY_NOT_FOUND,
                        "Không tìm thấy phiếu thu/chi"));
        return CashbookResponse.fromEntity(entity);
    }

    @Override
    @Transactional(readOnly = true)
    public List<CashbookResponse> getAllTransactions() {
        return cashbookRepository.findByDeletedFalseOrderByTransactionDateDesc().stream()
                .map(CashbookResponse::fromEntity)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<CashbookResponse> getTransactionsByBranch(Long branchId) {
        return cashbookRepository.findByBranchIdAndDeletedFalseOrderByTransactionDateDesc(branchId).stream()
                .map(CashbookResponse::fromEntity)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<CashbookResponse> getTransactionsByBranchAndType(Long branchId, TransactionType transactionType) {
        return cashbookRepository
                .findByBranchIdAndTransactionTypeAndDeletedFalseOrderByTransactionDateDesc(branchId, transactionType)
                .stream()
                .map(CashbookResponse::fromEntity)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<CashbookResponse> getTransactionsByContract(Long contractId) {
        return cashbookRepository.findByContractIdAndDeletedFalseOrderByTransactionDateDesc(contractId).stream()
                .map(CashbookResponse::fromEntity)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public CashFlowSummaryResponse getBranchCashFlowSummary(Long branchId) {
        Long summaryBranchId = null;
        String summaryBranchName = "Toàn hệ thống (Tất cả chi nhánh)";
        BigDecimal currentBalance = BigDecimal.ZERO;
        List<CashbookEntity> transactions;

        if (branchId != null) {
            BranchEntity branch = branchRepository.findByIdAndDeletedFalse(branchId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.BRANCH_NOT_FOUND, "Không tìm thấy chi nhánh"));
            summaryBranchId = branch.getId();
            summaryBranchName = branch.getBranchName();
            currentBalance = branch.getCurrentCashBalance() != null ? branch.getCurrentCashBalance() : BigDecimal.ZERO;
            transactions = cashbookRepository.findByBranchIdAndDeletedFalseOrderByTransactionDateDesc(branchId);
        } else {
            List<BranchEntity> activeBranches = branchRepository.findByActiveTrueAndDeletedFalseOrderByBranchNameAsc();
            if (activeBranches == null || activeBranches.isEmpty()) {
                activeBranches = branchRepository.findAllByDeletedFalseOrderByCreatedAtDesc();
            }
            if (activeBranches != null) {
                currentBalance = activeBranches.stream()
                        .map(b -> b.getCurrentCashBalance() != null ? b.getCurrentCashBalance() : BigDecimal.ZERO)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
            }
            transactions = cashbookRepository.findByDeletedFalseOrderByTransactionDateDesc();
        }

        BigDecimal totalIn = BigDecimal.ZERO;
        BigDecimal totalOut = BigDecimal.ZERO;
        BigDecimal disburse = BigDecimal.ZERO;
        BigDecimal interestCol = BigDecimal.ZERO;
        BigDecimal principalCol = BigDecimal.ZERO;
        BigDecimal liqIncome = BigDecimal.ZERO;

        if (transactions != null) {
            for (CashbookEntity t : transactions) {
                BigDecimal amt = t.getAmount() != null ? t.getAmount() : BigDecimal.ZERO;
                if (t.getTransactionType() == TransactionType.CASH_IN) {
                    totalIn = totalIn.add(amt);
                    if (t.getCategory() == TransactionCategory.INTEREST_COLLECTION) {
                        interestCol = interestCol.add(amt);
                    } else if (t.getCategory() == TransactionCategory.PRINCIPAL_COLLECTION) {
                        principalCol = principalCol.add(amt);
                    } else if (t.getCategory() == TransactionCategory.LIQUIDATION_INCOME) {
                        liqIncome = liqIncome.add(amt);
                    }
                } else if (t.getTransactionType() == TransactionType.CASH_OUT) {
                    totalOut = totalOut.add(amt);
                    if (t.getCategory() == TransactionCategory.DISBURSEMENT) {
                        disburse = disburse.add(amt);
                    }
                }
            }
        }

        BigDecimal netFlow = totalIn.subtract(totalOut);

        return CashFlowSummaryResponse.builder()
                .branchId(summaryBranchId)
                .branchName(summaryBranchName)
                .currentCashBalance(currentBalance)
                .totalCashIn(totalIn)
                .totalCashOut(totalOut)
                .netCashFlow(netFlow)
                .disbursementTotal(disburse)
                .interestCollectionTotal(interestCol)
                .principalCollectionTotal(principalCol)
                .liquidationIncomeTotal(liqIncome)
                .transactionCount(transactions != null ? (long) transactions.size() : 0L)
                .build();
    }

    // =========================================================================
    // MILESTONE 2: SCOPED CASHBOOK PERIOD QUERY SERVICE WRAPPER
    // =========================================================================

    @Override
    @Transactional(readOnly = true)
    public List<CashbookResponse> getTransactionsByScopeAndPeriod(
            Long branchId,
            LocalDateTime fromTime,
            LocalDateTime toTime
    ) {
        Long authorizedBranchId = branchSecurityGuard != null
                ? branchSecurityGuard.resolveAuthorizedBranchId(branchId)
                : branchId;

        LocalDateTime start = fromTime != null ? fromTime : LocalDate.now().withDayOfMonth(1).atStartOfDay();
        LocalDateTime end = toTime != null ? toTime : LocalDate.now().plusDays(1).atStartOfDay();

        return cashbookRepository.findByScopeAndTransactionDateBetween(authorizedBranchId, start, end)
                .stream()
                .map(CashbookResponse::fromEntity)
                .toList();
    }

    private String generateTransactionCode(TransactionType transactionType, LocalDateTime transactionDate) {
        return BusinessCodeGenerator.generateTransactionCode(transactionType, transactionDate);
    }
}
