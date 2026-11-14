package com.pawnmaster.api.modules.contract;

/**
 * Lifecycle status for each persisted periodic payment schedule cycle
 * ({@code dbo.contract_payment_schedules}).
 */
public enum ScheduleStatus {
    /** Projected at contract creation while awaiting approval (PENDING_APPROVAL). */
    PROJECTED,
    /** Official scheduled period after contract approval and disbursement. */
    SCHEDULED,
    /** Period whose due date has passed without settlement while contract is overdue. */
    OVERDUE,
    /** Settled normally via periodic interest or partial principal collection. */
    PAID,
    /** Settled early via contract redemption (pro-rata final cycle). */
    SETTLED_EARLY,
    /** Voided after contract rejection, early redemption, full payoff, or liquidation. */
    CANCELLED
}

