package com.pawnmaster.api.modules.user;

/**
 * System-wide RBAC roles and their authoritative operational capabilities.
 */
public enum RoleEnum {
    ADMIN(true, true, true),
    MANAGER(false, true, true),
    STAFF(false, false, true),
    ACCOUNTANT(false, false, false);

    private final boolean switchBranchAllowed;
    private final boolean approveOrLiquidateAllowed;
    private final boolean operateCounterAllowed;

    RoleEnum(
            boolean switchBranchAllowed,
            boolean approveOrLiquidateAllowed,
            boolean operateCounterAllowed) {
        this.switchBranchAllowed = switchBranchAllowed;
        this.approveOrLiquidateAllowed = approveOrLiquidateAllowed;
        this.operateCounterAllowed = operateCounterAllowed;
    }

    public boolean canSwitchBranch() {
        return switchBranchAllowed;
    }

    public boolean canApproveOrLiquidate() {
        return approveOrLiquidateAllowed;
    }

    public boolean canOperateCounter() {
        return operateCounterAllowed;
    }
}
