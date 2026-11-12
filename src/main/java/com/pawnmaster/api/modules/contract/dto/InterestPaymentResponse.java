package com.pawnmaster.api.modules.contract.dto;

import com.pawnmaster.api.modules.contract.InterestPaymentEntity;
import com.pawnmaster.api.modules.contract.PaymentType;
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
public class InterestPaymentResponse {

    private Long id;
    private String paymentCode;
    private Long contractId;
    private String contractCode;
    private Long collectionBranchId;
    private String collectionBranchCode;
    private String collectionBranchName;
    private PaymentType paymentType;
    private LocalDate fromDate;
    private LocalDate toDate;
    private Integer daysCount;
    private BigDecimal interestAmount;
    private BigDecimal storageFeeAmount;
    private BigDecimal penaltyAmount;
    private BigDecimal principalPaidAmount;
    private BigDecimal totalAmount;
    private LocalDateTime paymentDate;
    private String paymentMethod;
    private String notes;
    private LocalDateTime createdAt;

    public static InterestPaymentResponse fromEntity(InterestPaymentEntity entity) {
        if (entity == null) {
            return null;
        }

        Long cntId = null;
        String cntCode = null;
        if (entity.getContract() != null) {
            cntId = entity.getContract().getId();
            cntCode = entity.getContract().getContractCode();
        }

        Long collBranchId = entity.getCollectionBranchId();
        String collBranchCode = entity.getCollectionBranchCode();
        String collBranchName = entity.getCollectionBranchName();
        if (collBranchId == null && entity.getContract() != null && entity.getContract().getBranch() != null) {
            collBranchId = entity.getContract().getBranch().getId();
            collBranchCode = entity.getContract().getBranch().getBranchCode();
            collBranchName = entity.getContract().getBranch().getBranchName();
        } else if (collBranchId != null && collBranchCode == null
                && entity.getContract() != null
                && entity.getContract().getBranch() != null
                && collBranchId.equals(entity.getContract().getBranch().getId())) {
            collBranchCode = entity.getContract().getBranch().getBranchCode();
            collBranchName = entity.getContract().getBranch().getBranchName();
        }

        return InterestPaymentResponse.builder()
                .id(entity.getId())
                .paymentCode(entity.getPaymentCode())
                .contractId(cntId)
                .contractCode(cntCode)
                .collectionBranchId(collBranchId)
                .collectionBranchCode(collBranchCode)
                .collectionBranchName(collBranchName)
                .paymentType(entity.getPaymentType())
                .fromDate(entity.getFromDate())
                .toDate(entity.getToDate())
                .daysCount(entity.getDaysCount())
                .interestAmount(entity.getInterestAmount())
                .storageFeeAmount(entity.getStorageFeeAmount())
                .penaltyAmount(entity.getPenaltyAmount())
                .principalPaidAmount(entity.getPrincipalPaidAmount())
                .totalAmount(entity.getTotalAmount())
                .paymentDate(entity.getPaymentDate())
                .paymentMethod(entity.getPaymentMethod())
                .notes(entity.getNotes())
                .createdAt(entity.getCreatedAt())
                .build();
    }
}
