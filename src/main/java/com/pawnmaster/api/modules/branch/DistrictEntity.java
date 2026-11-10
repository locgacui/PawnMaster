package com.pawnmaster.api.modules.branch;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(
        name = "districts",
        indexes = {
                @Index(name = "idx_districts_province", columnList = "province_code"),
                @Index(name = "idx_districts_unit", columnList = "administrative_unit_id")
        }
)
public class DistrictEntity implements Serializable {

    @Id
    @Column(name = "code", length = 20, nullable = false)
    private String code;

    @Column(name = "name", nullable = false, columnDefinition = "NVARCHAR(255)")
    private String name;

    @Column(name = "name_en", columnDefinition = "NVARCHAR(255)")
    private String nameEn;

    @Column(name = "full_name", columnDefinition = "NVARCHAR(255)")
    private String fullName;

    @Column(name = "full_name_en", columnDefinition = "NVARCHAR(255)")
    private String fullNameEn;

    @Column(name = "code_name", length = 255)
    private String codeName;

    @Column(name = "province_code", length = 20)
    private String provinceCode;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "province_code", referencedColumnName = "code", insertable = false, updatable = false)
    private ProvinceEntity province;

    @Column(name = "administrative_unit_id")
    private Integer administrativeUnitId;
}
