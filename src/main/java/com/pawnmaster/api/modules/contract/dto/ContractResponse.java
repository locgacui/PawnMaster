package com.pawnmaster.api.modules.contract.dto;

import com.pawnmaster.api.modules.collateral.CollateralCategory;
import com.pawnmaster.api.modules.collateral.CollateralStatus;
import com.pawnmaster.api.modules.contract.ContractEntity;
import com.pawnmaster.api.modules.contract.ContractStatus;
import com.pawnmaster.api.modules.contract.InterestType;
import com.pawnmaster.api.modules.customer.CustomerOccupationGroup;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

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
    private String customerIdentityNumber;
    private CustomerOccupationGroup customerOccupationGroup;
    private Long collateralId;
    private String collateralCode;
    private String collateralName;
    private CollateralCategory collateralCategory;
    private BigDecimal collateralAppraisedValue;
    private String collateralSerialOrFrame;
    private String collateralLicensePlate;
    private String collateralStorageLocation;
    private CollateralStatus collateralStatus;
    private BigDecimal principalAmount;
    private BigDecimal remainingPrincipal;
    private BigDecimal appliedLtvPercent;
    private InterestType interestType;
    private BigDecimal interestRate;
    private BigDecimal storageFeeRate;
    private Integer loanTermDays;
    private Integer paymentCycleDays;
    private BigDecimal cycleInterestAmount;
    private LocalDate startDate;
    private LocalDate maturityDate;
    private LocalDate nextInterestDueDate;
    private Long daysUntilDue;
    private boolean dueToday;
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
        String cIdentity = null;
        CustomerOccupationGroup cOccGroup = null;
        if (entity.getCustomer() != null) {
            cId = entity.getCustomer().getId();
            cCode = entity.getCustomer().getCustomerCode();
            cName = entity.getCustomer().getFullName();
            cPhone = entity.getCustomer().getPhoneNumber();
            cIdentity = entity.getCustomer().getIdentityNumber();
            cOccGroup = entity.getCustomer().getOccupationGroup();
        }

        Long colId = null;
        String colCode = null;
        String colName = null;
        CollateralCategory colCat = null;
        BigDecimal colAppraised = null;
        String colSerial = null;
        String colPlate = null;
        String colStorage = null;
        CollateralStatus colStatus = null;
        if (entity.getCollateral() != null) {
            colId = entity.getCollateral().getId();
            colCode = entity.getCollateral().getCollateralCode();
            colName = entity.getCollateral().getName();
            colCat = entity.getCollateral().getCategory();
            colAppraised = entity.getCollateral().getAppraisedValue();
            colSerial = entity.getCollateral().getSerialOrFrameNumber();
            colPlate = entity.getCollateral().getLicensePlate();
            colStorage = entity.getCollateral().getStorageLocation();
            colStatus = entity.getCollateral().getStatus();
        }

        BigDecimal principal = entity.getRemainingPrincipal() != null && entity.getRemainingPrincipal().compareTo(BigDecimal.ZERO) > 0
                ? entity.getRemainingPrincipal()
                : (entity.getPrincipalAmount() != null ? entity.getPrincipalAmount() : BigDecimal.ZERO);
        BigDecimal rate = entity.getInterestRate() != null ? entity.getInterestRate() : BigDecimal.ZERO;
        BigDecimal storageRate = entity.getStorageFeeRate() != null ? entity.getStorageFeeRate() : BigDecimal.ZERO;
        int cycleDays = entity.getPaymentCycleDays() != null && entity.getPaymentCycleDays() > 0
                ? entity.getPaymentCycleDays()
                : 10;

        BigDecimal cycleInterest = BigDecimal.ZERO;
        InterestType intType = entity.getInterestType() != null ? entity.getInterestType() : InterestType.DAILY_PER_MILLION;
        if (principal.compareTo(BigDecimal.ZERO) > 0) {
            if (intType == InterestType.DAILY_PER_MILLION) {
                BigDecimal factor = principal.divide(new BigDecimal("1000000"), 8, RoundingMode.HALF_UP);
                cycleInterest = factor.multiply(rate.add(storageRate))
                        .multiply(BigDecimal.valueOf(cycleDays))
                        .setScale(0, RoundingMode.HALF_UP);
            } else if (intType == InterestType.PERCENT_MONTHLY) {
                BigDecimal totalMonthlyPct = rate.add(storageRate).divide(new BigDecimal("100"), 8, RoundingMode.HALF_UP);
                BigDecimal dayRatio = BigDecimal.valueOf(cycleDays).divide(new BigDecimal("30"), 8, RoundingMode.HALF_UP);
                cycleInterest = principal.multiply(totalMonthlyPct).multiply(dayRatio).setScale(0, RoundingMode.HALF_UP);
            } else {
                BigDecimal totalWeeklyPct = rate.add(storageRate).divide(new BigDecimal("100"), 8, RoundingMode.HALF_UP);
                BigDecimal dayRatio = BigDecimal.valueOf(cycleDays).divide(new BigDecimal("7"), 8, RoundingMode.HALF_UP);
                cycleInterest = principal.multiply(totalWeeklyPct).multiply(dayRatio).setScale(0, RoundingMode.HALF_UP);
            }
        }

        Long daysUntilDue = null;
        boolean dueToday = false;
        if (entity.getNextInterestDueDate() != null) {
            LocalDate today = LocalDate.now();
            daysUntilDue = ChronoUnit.DAYS.between(today, entity.getNextInterestDueDate());
            dueToday = daysUntilDue == 0L
                    && (entity.getStatus() == ContractStatus.ACTIVE || entity.getStatus() == ContractStatus.EXTENDED);
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
                .customerIdentityNumber(cIdentity)
                .customerOccupationGroup(cOccGroup)
                .collateralId(colId)
                .collateralCode(colCode)
                .collateralName(colName)
                .collateralCategory(colCat)
                .collateralAppraisedValue(colAppraised)
                .collateralSerialOrFrame(colSerial)
                .collateralLicensePlate(colPlate)
                .collateralStorageLocation(colStorage)
                .collateralStatus(colStatus)
                .principalAmount(entity.getPrincipalAmount())
                .remainingPrincipal(entity.getRemainingPrincipal())
                .appliedLtvPercent(entity.getAppliedLtvPercent())
                .interestType(entity.getInterestType())
                .interestRate(entity.getInterestRate())
                .storageFeeRate(entity.getStorageFeeRate())
                .loanTermDays(entity.getLoanTermDays())
                .paymentCycleDays(entity.getPaymentCycleDays())
                .cycleInterestAmount(cycleInterest)
                .startDate(entity.getStartDate())
                .maturityDate(entity.getMaturityDate())
                .nextInterestDueDate(entity.getNextInterestDueDate())
                .daysUntilDue(daysUntilDue)
                .dueToday(dueToday)
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
