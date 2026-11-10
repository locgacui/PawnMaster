package com.pawnmaster.api.modules.branch;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Composite Test Suite verifying Checkpoint 1 (Branch, User & Auth Services).
 * Invoked via: mvn test -Dtest=BranchAndAuthServiceTest
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Checkpoint 1: Foundation (Branch, User & Auth RBAC) Suite")
public class BranchAndAuthServiceTest {

    @Nested
    @DisplayName("1. BranchService Tests")
    class BranchTests extends BranchServiceTest {
    }

    @Nested
    @DisplayName("2. UserService Tests")
    class UserTests extends com.pawnmaster.api.modules.user.UserServiceTest {
    }

    @Nested
    @DisplayName("3. AuthService Tests")
    class AuthTests extends com.pawnmaster.api.modules.user.AuthServiceTest {
    }
}
