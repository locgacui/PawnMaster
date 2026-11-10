package com.pawnmaster.api.modules.branch;

import com.pawnmaster.api.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
        name = "branches",
        indexes = {
                @Index(name = "idx_branches_code", columnList = "branch_code", unique = true),
                @Index(name = "idx_branches_province_district", columnList = "province_code, district_code")
        }
)
public class BranchEntity extends BaseEntity {

    @Column(name = "branch_code", nullable = false, unique = true, length = 50)
    private String branchCode;

    @Column(name = "branch_name", nullable = false, columnDefinition = "NVARCHAR(150)")
    private String branchName;

    @Column(name = "province_code", nullable = false, length = 20)
    private String provinceCode;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "province_code", referencedColumnName = "code", insertable = false, updatable = false, foreignKey = @ForeignKey(name = "fk_branches_province"))
    private ProvinceEntity province;

    @Column(name = "district_code", nullable = false, length = 20)
    private String districtCode;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "district_code", referencedColumnName = "code", insertable = false, updatable = false, foreignKey = @ForeignKey(name = "fk_branches_district"))
    private DistrictEntity district;

    @Column(name = "ward_code", length = 20)
    private String wardCode;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ward_code", referencedColumnName = "code", insertable = false, updatable = false, foreignKey = @ForeignKey(name = "fk_branches_ward"))
    private WardEntity ward;

    @Column(name = "address_detail", columnDefinition = "NVARCHAR(255)")
    private String addressDetail;

    @Column(name = "phone_number", length = 20)
    private String phoneNumber;

    @Column(name = "capital_limit", nullable = false, precision = 18, scale = 2)
    @Builder.Default
    private BigDecimal capitalLimit = new BigDecimal("2500000000.00");

    @Column(name = "current_cash_balance", nullable = false, precision = 18, scale = 2)
    @Builder.Default
    private BigDecimal currentCashBalance = new BigDecimal("500000000.00");

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private boolean active = true;
}
