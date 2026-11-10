package com.pawnmaster.api.modules.collateral;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Composite Test Suite verifying Checkpoint 2 (Customer, Collateral & 2D LTV Matrix Policies).
 * Groups all Checkpoint 2 unit tests for pure in-memory execution (< 1 second).
 *
 * Invoked via:
 *   mvn test -Dtest=CustomerAndCollateralServiceTest
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Checkpoint 2: Risk Underwriting (Customer, Collateral & 2D LTV Matrix) Suite")
public class CustomerAndCollateralServiceTest {

    @Nested
    @DisplayName("1. 2D LTV Matrix & Document Verification Policy Tests")
    class LtvMatrixPolicyTests extends LtvMatrixPolicyTest {
    }

    @Nested
    @DisplayName("2. CustomerService Tests")
    class CustomerTests extends com.pawnmaster.api.modules.customer.CustomerServiceTest {
    }

    @Nested
    @DisplayName("3. CollateralService Tests")
    class CollateralTests extends CollateralServiceTest {
    }
}
