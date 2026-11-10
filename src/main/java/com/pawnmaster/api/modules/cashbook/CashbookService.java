package com.pawnmaster.api.modules.cashbook;

import com.pawnmaster.api.modules.cashbook.dto.CashbookCreateRequest;
import com.pawnmaster.api.modules.cashbook.dto.CashbookResponse;
import com.pawnmaster.api.modules.cashbook.dto.CashFlowSummaryResponse;

import java.math.BigDecimal;
import java.util.List;

public interface CashbookService {

    CashbookResponse recordTransaction(CashbookCreateRequest request);

    CashbookResponse collectCashIn(Long branchId, BigDecimal amount, TransactionCategory category,
                                  Long contractId, Long userId, String notes);

    CashbookResponse collectCashIn(Long branchId, BigDecimal amount, String category,
                                  Long contractId, Long userId, String notes);

    CashbookResponse disburseLoan(Long branchId, BigDecimal amount, Long contractId,
                                 Long userId, String notes);

    CashbookResponse recordCashIn(Long branchId, BigDecimal amount, TransactionCategory category,
                                 Long contractId, Long customerId, String payerName,
                                 String description, String paymentMethod, Long userId);

    CashbookResponse recordCashOut(Long branchId, BigDecimal amount, TransactionCategory category,
                                  Long contractId, Long customerId, String receiverName,
                                  String description, String paymentMethod, Long userId);

    CashbookResponse getTransactionById(Long id);

    CashbookResponse getTransactionByCode(String code);

    List<CashbookResponse> getAllTransactions();

    List<CashbookResponse> getTransactionsByBranch(Long branchId);

    List<CashbookResponse> getTransactionsByBranchAndType(Long branchId, TransactionType transactionType);

    List<CashbookResponse> getTransactionsByContract(Long contractId);

    CashFlowSummaryResponse getBranchCashFlowSummary(Long branchId);
}
