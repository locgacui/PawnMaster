package com.pawnmaster.api.modules.contract;

import com.pawnmaster.api.modules.cashbook.CashbookServiceTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Composite Test Suite verifying Checkpoint 3 (Financial Operations Engine: Cashbook, Maker-Checker & Contract Servicing).
 * Groups all Checkpoint 3 unit and service tests for pure in-memory execution (< 1 second).
 *
 * Invoked via:
 *   mvn test -Dtest=Milestone3FinancialEngineTest
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Checkpoint 3: Financial Operations Engine (Cashbook, Maker-Checker & Contract Servicing) Suite")
public class Milestone3FinancialEngineTest {

    @Nested
    @DisplayName("1. Cashbook Ledger & Cash Flow Service Tests")
    class CashbookServiceTests extends CashbookServiceTest {
    }

    @Nested
    @DisplayName("2. Contract Maker-Checker Approval Workflow Tests")
    class ContractApprovalWorkflowTests extends ContractApprovalWorkflowTest {
    }

    @Nested
    @DisplayName("3. Contract Lifecycle, Servicing & Calculations Tests")
    class ContractLifecycleServiceTests extends ContractLifecycleServiceTest {
    }
}
