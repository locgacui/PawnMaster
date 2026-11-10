package com.pawnmaster.api.modules.collateral.dto;

import com.pawnmaster.api.modules.collateral.CollateralCategory;
import com.pawnmaster.api.modules.collateral.CollateralStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CollateralResponse {

    private Long id;
    private String collateralCode;
    private Long customerId;
    private String customerCode;
    private String customerName;
    private Long branchId;
    private String branchCode;
    private String branchName;
    private String name;
    private CollateralCategory category;
    private String brand;
    private String model;
    private String serialOrFrameNumber;
    private String licensePlate;
    private String conditionDescription;
    private BigDecimal appraisedValue;
    private BigDecimal maxLtvPercent;
    private BigDecimal maxAllowedLoanAmount;
    private String storageLocation;
    private String imageUrls;
    private CollateralStatus status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
