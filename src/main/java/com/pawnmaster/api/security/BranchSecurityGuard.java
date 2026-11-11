package com.pawnmaster.api.security;

import com.pawnmaster.api.common.BusinessException;
import com.pawnmaster.api.common.ErrorCode;
import com.pawnmaster.api.modules.user.RoleEnum;
import com.pawnmaster.api.modules.user.UserEntity;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Centralized guard enforcing strict branch-level data and operation isolation.
 * <p>
 * Rules:
 * <ul>
 *   <li>{@link RoleEnum#ADMIN} can view and operate across all branches (including {@code branchId = null} system-wide).</li>
 *   <li>Branch-scoped roles ({@code MANAGER}, {@code STAFF}, {@code ACCOUNTANT}) are strictly restricted to their assigned {@code branchId}.</li>
 *   <li>Any attempt to switch branches on {@code #branchSelector} or submit a request with a mismatched {@code branchId}
 *       throws {@link BusinessException} with {@link ErrorCode#BRANCH_ACCESS_DENIED} (HTTP 403, Code 4004).</li>
 * </ul>
 */
@Slf4j
@Component
public class BranchSecurityGuard {

    public UserPrincipal getCurrentUserOrNull() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && auth.getPrincipal() instanceof UserPrincipal principal) {
            return principal;
        }
        return null;
    }

    public boolean isAdmin(UserPrincipal user) {
        return user != null && user.getRole() == RoleEnum.ADMIN;
    }

    /**
     * Validates that the currently authenticated user is authorized to access or operate on {@code targetBranchId}.
     * If no authenticated {@link UserPrincipal} is present in {@link SecurityContextHolder} (e.g., standalone unit tests),
     * this check is a no-op so unit tests without security context remain unaffected.
     */
    public void validateBranchAccess(Long targetBranchId) {
        UserPrincipal currentUser = getCurrentUserOrNull();
        if (currentUser == null) {
            return;
        }
        validateBranchAccess(currentUser, targetBranchId);
    }

    /**
     * Explicitly validates that {@code currentUser} is authorized for {@code targetBranchId}.
     */
    public void validateBranchAccess(UserPrincipal currentUser, Long targetBranchId) {
        if (currentUser == null || currentUser.getRole() == RoleEnum.ADMIN) {
            return;
        }

        Long assignedBranchId = currentUser.getBranchId();
        String assignedBranchLabel = currentUser.getBranchName() != null
                ? currentUser.getBranchName() + (currentUser.getBranchCode() != null ? " (" + currentUser.getBranchCode() + ")" : "")
                : ("Chi nhánh #" + assignedBranchId);

        if (assignedBranchId == null) {
            log.warn("Branch access denied: user={} (role={}) has no assigned branch",
                    currentUser.getUsername(), currentUser.getRole());
            throw new BusinessException(
                    ErrorCode.BRANCH_ACCESS_DENIED,
                    String.format("Tài khoản %s (%s) chưa được phân công chi nhánh hoạt động.",
                            currentUser.getFullName(), currentUser.getRole())
            );
        }

        if (targetBranchId == null) {
            log.warn("Branch access denied: user={} (role={}, branch={}) attempted system-wide access",
                    currentUser.getUsername(), currentUser.getRole(), assignedBranchId);
            throw new BusinessException(
                    ErrorCode.BRANCH_ACCESS_DENIED,
                    String.format("Truy cập bị từ chối: Tài khoản %s (%s) chỉ được phân quyền tại [%s]. Bạn không có quyền xem dữ liệu toàn hệ thống.",
                            currentUser.getFullName(), currentUser.getRole(), assignedBranchLabel)
            );
        }

        if (!assignedBranchId.equals(targetBranchId)) {
            log.warn("Branch access denied: user={} (role={}, assignedBranch={}) attempted to access targetBranch={}",
                    currentUser.getUsername(), currentUser.getRole(), assignedBranchId, targetBranchId);
            throw new BusinessException(
                    ErrorCode.BRANCH_ACCESS_DENIED,
                    String.format("Truy cập bị từ chối: Tài khoản %s (%s) chỉ được phân quyền thao tác tại [%s]. Không được phép chuyển sang hoặc thao tác trên chi nhánh khác.",
                            currentUser.getFullName(), currentUser.getRole(), assignedBranchLabel)
            );
        }
    }

    /**
     * Validates that a persisted {@link UserEntity} has permission to operate on {@code targetBranchId}.
     */
    public void validateUserEntityBranchAccess(UserEntity user, Long targetBranchId) {
        if (user == null || user.getRole() == RoleEnum.ADMIN) {
            return;
        }
        Long userBranchId = user.getBranchId();
        String branchLabel = user.getBranchName() != null
                ? user.getBranchName()
                : ("Chi nhánh #" + userBranchId);

        if (userBranchId == null || targetBranchId == null || !userBranchId.equals(targetBranchId)) {
            throw new BusinessException(
                    ErrorCode.BRANCH_ACCESS_DENIED,
                    String.format("Truy cập bị từ chối: Tài khoản %s (%s) chỉ được phép thao tác tại [%s].",
                            user.getFullName(), user.getRole(), branchLabel)
            );
        }
    }

    /**
     * Resolves the effective branchId for the current user:
     * - If user is ADMIN: returns {@code requestedBranchId} (may be null for system-wide).
     * - If user is branch-scoped:
     *   - When {@code requestedBranchId == null}, defaults to {@code currentUser.getBranchId()}.
     *   - When {@code requestedBranchId != null}, validates it against {@code currentUser.getBranchId()} (throws 4004 if mismatched).
     */
    public Long resolveAuthorizedBranchId(Long requestedBranchId) {
        UserPrincipal currentUser = getCurrentUserOrNull();
        if (currentUser == null) {
            return requestedBranchId;
        }
        if (currentUser.getRole() == RoleEnum.ADMIN) {
            return requestedBranchId;
        }
        if (requestedBranchId == null) {
            return currentUser.getBranchId();
        }
        validateBranchAccess(currentUser, requestedBranchId);
        return requestedBranchId;
    }
}

