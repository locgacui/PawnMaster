package com.pawnmaster.api.modules.collateral;

import com.pawnmaster.api.common.BaseEntity;
import com.pawnmaster.api.modules.customer.CustomerOccupationGroup;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
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
        name = "ltv_policies",
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_ltv_policies_group_category", columnNames = {"occupation_group", "collateral_category"})
        },
        indexes = {
                @Index(name = "idx_ltv_policies_group_cat", columnList = "occupation_group, collateral_category", unique = true)
        }
)
public class LtvPolicyEntity extends BaseEntity {

    @Enumerated(EnumType.STRING)
    @Column(name = "occupation_group", nullable = false, length = 50)
    private CustomerOccupationGroup occupationGroup;

    @Enumerated(EnumType.STRING)
    @Column(name = "collateral_category", nullable = false, length = 50)
    private CollateralCategory collateralCategory;

    @Column(name = "max_ltv_percent", nullable = false, precision = 5, scale = 2)
    private BigDecimal maxLtvPercent;

    @Column(name = "description", columnDefinition = "NVARCHAR(255)")
    private String description;
}

