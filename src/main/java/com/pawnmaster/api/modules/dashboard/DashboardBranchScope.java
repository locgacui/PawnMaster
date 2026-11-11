package com.pawnmaster.api.modules.dashboard;

import com.pawnmaster.api.modules.branch.BranchService;
import com.pawnmaster.api.modules.branch.dto.BranchResponse;
import com.pawnmaster.api.security.UserPrincipal;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Immutable RBAC capability and branch-visibility scope shared across Dashboard SSR and REST flows.
 * Centralizes branch lookup, single-branch isolation, and fail-closed role evaluation.
 */
@Slf4j
record DashboardBranchScope(
        BranchResponse selectedBranch,
        List<BranchResponse> visibleBranches,
        boolean canSwitchBranch,
        boolean canApproveOrLiquidate,
        boolean canOperateCounter
) {

    static DashboardBranchScope resolve(
            Long targetBranchId,
            List<BranchResponse> rawBranches,
            UserPrincipal currentUser,
            BranchService branchService
    ) {
        List<BranchResponse> safeBranches = sanitizeBranches(rawBranches);

        // Standalone/unauthenticated execution preserves full preview capabilities;
        // authenticated principals with a missing role fail closed.
        boolean canSwitchBranch = currentUser == null
                || (currentUser.getRole() != null && currentUser.getRole().canSwitchBranch());
        boolean canApproveOrLiquidate = currentUser == null
                || (currentUser.getRole() != null && currentUser.getRole().canApproveOrLiquidate());
        boolean canOperateCounter = currentUser == null
                || (currentUser.getRole() != null && currentUser.getRole().canOperateCounter());

        Long effectiveTargetId = targetBranchId != null
                ? targetBranchId
                : (!canSwitchBranch && currentUser != null ? currentUser.getBranchId() : null);

        BranchResponse selectedBranch = resolveSelectedBranch(
                effectiveTargetId,
                safeBranches,
                currentUser,
                canSwitchBranch,
                branchService
        );

        List<BranchResponse> visibleBranches = resolveVisibleBranches(
                canSwitchBranch,
                selectedBranch,
                effectiveTargetId,
                safeBranches
        );

        return new DashboardBranchScope(
                selectedBranch,
                visibleBranches,
                canSwitchBranch,
                canApproveOrLiquidate,
                canOperateCounter
        );
    }

    private static BranchResponse resolveSelectedBranch(
            Long targetBranchId,
            List<BranchResponse> safeBranches,
            UserPrincipal currentUser,
            boolean canSwitchBranch,
            BranchService branchService
    ) {
        if (targetBranchId != null) {
            Optional<BranchResponse> found = findBranchInListOrFetch(targetBranchId, safeBranches, branchService);
            if (found.isPresent()) {
                return found.get();
            }
            if (!canSwitchBranch && currentUser != null && targetBranchId.equals(currentUser.getBranchId())) {
                return buildPrincipalFallbackBranch(currentUser);
            }
            return null;
        }

        // Only unauthenticated standalone mode defaults to the first active branch.
        // Authenticated ADMIN with branchId == null represents system-wide ("ALL") scope,
        // and an authenticated non-admin without an assigned branch must NEVER leak branches.get(0).
        if (currentUser == null && !safeBranches.isEmpty()) {
            return safeBranches.get(0);
        }
        return null;
    }

    private static List<BranchResponse> resolveVisibleBranches(
            boolean canSwitchBranch,
            BranchResponse selectedBranch,
            Long effectiveTargetId,
            List<BranchResponse> safeBranches
    ) {
        if (canSwitchBranch) {
            return safeBranches;
        }
        if (selectedBranch != null) {
            return List.of(selectedBranch);
        }
        if (effectiveTargetId != null) {
            return safeBranches.stream()
                    .filter(b -> effectiveTargetId.equals(b.getId()))
                    .limit(1)
                    .toList();
        }
        return List.of();
    }

    static Optional<BranchResponse> findBranchInListOrFetch(
            Long branchId,
            List<BranchResponse> safeBranches,
            BranchService branchService
    ) {
        if (branchId == null) {
            return Optional.empty();
        }
        Optional<BranchResponse> inMemory = safeBranches.stream()
                .filter(b -> branchId.equals(b.getId()))
                .findFirst();
        if (inMemory.isPresent() || branchService == null) {
            return inMemory;
        }
        try {
            return Optional.ofNullable(branchService.getBranchById(branchId));
        } catch (Exception ex) {
            log.debug("Unable to fetch branch id={} from BranchService: {}", branchId, ex.getMessage());
            return Optional.empty();
        }
    }

    private static BranchResponse buildPrincipalFallbackBranch(UserPrincipal user) {
        Long branchId = user.getBranchId();
        return BranchResponse.builder()
                .id(branchId)
                .branchCode(user.getBranchCode() != null ? user.getBranchCode() : ("CN" + branchId))
                .branchName(user.getBranchName() != null ? user.getBranchName() : ("Chi nhánh #" + branchId))
                .active(true)
                .build();
    }

    static List<BranchResponse> sanitizeBranches(List<BranchResponse> branches) {
        if (branches == null || branches.isEmpty()) {
            return List.of();
        }
        return branches.stream()
                .filter(Objects::nonNull)
                .toList();
    }
}
