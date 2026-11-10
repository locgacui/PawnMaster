package com.pawnmaster.api.modules.collateral.dto;

import com.pawnmaster.api.modules.collateral.CollateralCategory;
import com.pawnmaster.api.modules.collateral.CollateralStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
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
public class CollateralCreateRequest {

    @Size(max = 50)
    private String collateralCode;

    @NotNull(message = "ID khách hàng không được để trống")
    private Long customerId;

    private Long branchId;

    @NotBlank(message = "Tên tài sản không được để trống")
    @Size(max = 200)
    private String name;

    @NotNull(message = "Loại tài sản không được để trống")
    private CollateralCategory category;

    private String brand;
    private String model;
    private String serialOrFrameNumber;
    private String licensePlate;
    private String conditionDescription;

    @NotNull(message = "Giá trị định giá không được để trống")
    @Positive(message = "Giá trị định giá phải > 0")
    private BigDecimal appraisedValue;

    private BigDecimal maxLtvPercent;
    private String storageLocation;
    private String imageUrls;

    @Builder.Default
    private CollateralStatus status = CollateralStatus.IN_VAULT;
}
