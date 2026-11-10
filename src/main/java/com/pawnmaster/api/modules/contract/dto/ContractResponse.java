package com.pawnmaster.api.modules.contract.dto;

import com.pawnmaster.api.modules.collateral.CollateralCategory;
import com.pawnmaster.api.modules.contract.ContractEntity;
import com.pawnmaster.api.modules.contract.ContractStatus;
import com.pawnmaster.api.modules.contract.InterestType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ContractResponse {

    private Long id;
    private String contractCode;
    private Long branchId;
    private String branchCode;
    private String branchName;
    private Long customerId;
    private String customerCode;
    private String customerName;
    private String customerPhone;
    private Long collateralId;
    private String collateralCode;
    private String collateralName;
    private CollateralCategory collateralCategory;
    private BigDecimal collateralAppraisedValue;
    private BigDecimal principalAmount;
    private BigDecimal remainingPrincipal;
    private BigDecimal appliedLtvPercent;
    private InterestType interestType;
    private BigDecimal interestRate;
    private BigDecimal storageFeeRate;
    private Integer loanTermDays;
    private Integer paymentCycleDays;
    private LocalDate startDate;
    private LocalDate maturityDate;
    private LocalDate nextInterestDueDate;
    private LocalDate closedDate;
    private BigDecimal totalInterestPaid;
    private ContractStatus status;
    private Long approvedBy;
    private LocalDateTime approvedAt;
    private String approvalNotes;
    private String notes;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static ContractResponse fromEntity(ContractEntity entity) {
        if (entity == null) {
            return null;
        }

        Long bId = null;
        String bCode = null;
        String bName = null;
        if (entity.getBranch() != null) {
            bId = entity.getBranch().getId();
            bCode = entity.getBranch().getBranchCode();
            bName = entity.getBranch().getBranchName();
        }

        Long cId = null;
        String cCode = null;
        String cName = null;
        String cPhone = null;
        if (entity.getCustomer() != null) {
            cId = entity.getCustomer().getId();
            cCode = entity.getCustomer().getCustomerCode();
            cName = entity.getCustomer().getFullName();
            cPhone = entity.getCustomer().getPhoneNumber();
        }

        Long colId = null;
        String colCode = null;
        String colName = null;
        CollateralCategory colCat = null;
        BigDecimal colAppraised = null;
        if (entity.getCollateral() != null) {
            colId = entity.getCollateral().getId();
            colCode = entity.getCollateral().getCollateralCode();
            colName = entity.getCollateral().getName();
            colCat = entity.getCollateral().getCategory();
            colAppraised = entity.getCollateral().getAppraisedValue();
        }

        return ContractResponse.builder()
                .id(entity.getId())
                .contractCode(entity.getContractCode())
                .branchId(bId)
                .branchCode(bCode)
                .branchName(bName)
                .customerId(cId)
                .customerCode(cCode)
                .customerName(cName)
                .customerPhone(cPhone)
                .collateralId(colId)
                .collateralCode(colCode)
                .collateralName(colName)
                .collateralCategory(colCat)
                .collateralAppraisedValue(colAppraised)
                .principalAmount(entity.getPrincipalAmount())
                .remainingPrincipal(entity.getRemainingPrincipal())
                .appliedLtvPercent(entity.getAppliedLtvPercent())
                .interestType(entity.getInterestType())
                .interestRate(entity.getInterestRate())
                .storageFeeRate(entity.getStorageFeeRate())
                .loanTermDays(entity.getLoanTermDays())
                .paymentCycleDays(entity.getPaymentCycleDays())
                .startDate(entity.getStartDate())
                .maturityDate(entity.getMaturityDate())
                .nextInterestDueDate(entity.getNextInterestDueDate())
                .closedDate(entity.getClosedDate())
                .totalInterestPaid(entity.getTotalInterestPaid())
                .status(entity.getStatus())
                .approvedBy(entity.getApprovedBy())
                .approvedAt(entity.getApprovedAt())
                .approvalNotes(entity.getApprovalNotes())
                .notes(entity.getNotes())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}
