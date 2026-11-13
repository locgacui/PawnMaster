package com.pawnmaster.api.modules.collateral;

import com.pawnmaster.api.common.BaseEntity;
import com.pawnmaster.api.modules.branch.BranchEntity;
import com.pawnmaster.api.modules.customer.CustomerEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(
        name = "collaterals",
        indexes = {
                @Index(name = "idx_collaterals_code", columnList = "collateral_code", unique = true),
                @Index(name = "idx_collaterals_customer", columnList = "customer_id"),
                @Index(name = "idx_collaterals_branch", columnList = "branch_id"),
                @Index(name = "idx_collaterals_status", columnList = "status"),
                @Index(name = "idx_collaterals_serial", columnList = "serial_or_frame_number")
        }
)
public class CollateralEntity extends BaseEntity {

    @Column(name = "collateral_code", nullable = false, unique = true, length = 50)
    private String collateralCode;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false, foreignKey = @ForeignKey(name = "fk_collaterals_customer"))
    private CustomerEntity customer;

    @Column(name = "customer_id", insertable = false, updatable = false)
    private Long customerId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "branch_id", foreignKey = @ForeignKey(name = "fk_collaterals_branch"))
    private BranchEntity branch;

    @Column(name = "branch_id", insertable = false, updatable = false)
    private Long branchId;

    @Column(name = "name", nullable = false, columnDefinition = "NVARCHAR(200)")
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false, length = 50)
    private CollateralCategory category;

    @Column(name = "brand", columnDefinition = "NVARCHAR(100)")
    private String brand;

    @Column(name = "model", columnDefinition = "NVARCHAR(100)")
    private String model;

    @Column(name = "serial_or_frame_number", columnDefinition = "NVARCHAR(255)")
    private String serialOrFrameNumber; // Tóm tắt định danh (Số khung/Số máy, IMEI/Serial, Vàng...)

    @Column(name = "license_plate", length = 30)
    private String licensePlate; // Biển kiểm soát (nếu là xe máy/ô tô)

    @Column(name = "condition_description", columnDefinition = "NVARCHAR(500)")
    private String conditionDescription;

    @Column(name = "appraised_value", nullable = false, precision = 18, scale = 2)
    private BigDecimal appraisedValue;

    @Column(name = "max_ltv_percent", precision = 5, scale = 2)
    private BigDecimal maxLtvPercent;

    @Column(name = "storage_location", columnDefinition = "NVARCHAR(150)")
    private String storageLocation; // Vị trí két/kho lưu trữ

    @Column(name = "image_urls", columnDefinition = "NVARCHAR(1000)")
    private String imageUrls;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    @Builder.Default
    private CollateralStatus status = CollateralStatus.IN_VAULT;

    public Long getBranchId() {
        return branch != null ? branch.getId() : branchId;
    }

    public String getBranchCode() {
        return branch != null ? branch.getBranchCode() : null;
    }

    public String getBranchName() {
        return branch != null ? branch.getBranchName() : null;
    }

    public Long getCustomerId() {
        return customer != null ? customer.getId() : customerId;
    }

    public String getCustomerName() {
        return customer != null ? customer.getFullName() : null;
    }
}
