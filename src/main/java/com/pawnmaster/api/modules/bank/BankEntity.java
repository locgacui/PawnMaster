package com.pawnmaster.api.modules.bank;

import com.pawnmaster.api.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Reference entity representing a Vietnamese commercial bank in {@code dbo.banks}.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(
        name = "banks",
        indexes = {
                @Index(name = "idx_banks_code", columnList = "bank_code", unique = true),
                @Index(name = "idx_banks_group_order", columnList = "bank_group, display_order")
        }
)
public class BankEntity extends BaseEntity {

    @Column(name = "bank_code", nullable = false, unique = true, length = 20)
    private String bankCode;

    @Column(name = "bin", length = 10)
    private String bin;

    @Column(name = "short_name", nullable = false, columnDefinition = "NVARCHAR(60)")
    private String shortName;

    @Column(name = "full_name", nullable = false, columnDefinition = "NVARCHAR(200)")
    private String fullName;

    @Column(name = "bank_group", nullable = false, length = 30)
    @Builder.Default
    private String bankGroup = "COMMERCIAL";

    @Column(name = "display_order", nullable = false)
    @Builder.Default
    private Integer displayOrder = 100;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean active = true;
}

