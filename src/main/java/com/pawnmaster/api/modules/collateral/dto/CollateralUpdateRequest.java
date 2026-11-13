package com.pawnmaster.api.modules.collateral.dto;

import com.pawnmaster.api.modules.collateral.CollateralCategory;
import com.pawnmaster.api.modules.collateral.CollateralStatus;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CollateralUpdateRequest {

    @Size(max = 200)
    private String name;

    private CollateralCategory category;
    private String brand;
    private String model;
    private String serialOrFrameNumber;
    private String licensePlate;
    private String vehicleFrameNumber;
    private String vehicleRegNumber;

    @Min(value = 0, message = "Chỉ số ODO (km) phải >= 0")
    private Integer vehicleOdometerKm;

    private String techImeiOrSerial;
    private String techPasscodeStatus;
    private String techAccessories;
    private String goldPurity;

    @DecimalMin(value = "0.001", message = "Trọng lượng vàng phải > 0 chỉ")
    private BigDecimal goldWeightChi;

    private String goldCertificateCode;
    private String otherAssetSpec;
    private String conditionDescription;

    @Positive(message = "Giá trị định giá phải > 0")
    private BigDecimal appraisedValue;

    private BigDecimal maxLtvPercent;
    private String storageLocation;
    private String imageUrls;
    private CollateralStatus status;
}
