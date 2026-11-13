package com.pawnmaster.api.modules.customer;

import com.pawnmaster.api.common.BaseEntity;
import com.pawnmaster.api.modules.branch.DistrictEntity;
import com.pawnmaster.api.modules.branch.ProvinceEntity;
import com.pawnmaster.api.modules.branch.WardEntity;
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
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(
        name = "customers",
        indexes = {
                @Index(name = "idx_customers_code", columnList = "customer_code", unique = true),
                @Index(name = "idx_customers_identity", columnList = "identity_number", unique = true),
                @Index(name = "idx_customers_phone", columnList = "phone_number"),
                @Index(name = "idx_customers_occupation", columnList = "occupation_group"),
                @Index(name = "idx_customers_location", columnList = "province_code, district_code, ward_code")
        }
)
public class CustomerEntity extends BaseEntity {

    @Column(name = "customer_code", nullable = false, unique = true, length = 50)
    private String customerCode;

    @Column(name = "full_name", nullable = false, columnDefinition = "NVARCHAR(150)")
    private String fullName;

    @Column(name = "identity_number", nullable = false, unique = true, length = 25)
    private String identityNumber;

    @Column(name = "identity_issue_date")
    private LocalDate identityIssueDate;

    @Column(name = "identity_issue_place", columnDefinition = "NVARCHAR(150)")
    private String identityIssuePlace;

    @Column(name = "phone_number", nullable = false, length = 20)
    private String phoneNumber;

    @Column(name = "email", length = 120)
    private String email;

    @Column(name = "date_of_birth")
    private LocalDate dateOfBirth;

    @Column(name = "gender", columnDefinition = "NVARCHAR(20)")
    private String gender;

    @Column(name = "province_code", length = 20)
    private String provinceCode;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "province_code", referencedColumnName = "code", insertable = false, updatable = false, foreignKey = @ForeignKey(name = "fk_customers_province"))
    private ProvinceEntity province;

    @Column(name = "district_code", length = 20)
    private String districtCode;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "district_code", referencedColumnName = "code", insertable = false, updatable = false, foreignKey = @ForeignKey(name = "fk_customers_district"))
    private DistrictEntity district;

    @Column(name = "ward_code", length = 20)
    private String wardCode;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ward_code", referencedColumnName = "code", insertable = false, updatable = false, foreignKey = @ForeignKey(name = "fk_customers_ward"))
    private WardEntity ward;

    @Column(name = "address", columnDefinition = "NVARCHAR(255)")
    private String address;

    @Column(name = "permanent_address", columnDefinition = "NVARCHAR(255)")
    private String permanentAddress;

    @Enumerated(EnumType.STRING)
    @Column(name = "occupation_group", nullable = false, length = 40)
    @Builder.Default
    private CustomerOccupationGroup occupationGroup = CustomerOccupationGroup.FREELANCER;

    @Column(name = "monthly_income", precision = 18, scale = 2)
    private BigDecimal monthlyIncome;

    @Column(name = "has_social_insurance", nullable = false)
    @Builder.Default
    private boolean hasSocialInsurance = false;

    @Column(name = "verification_doc_type", length = 50)
    private String verificationDocType;

    @Column(name = "verification_doc_urls", length = 1000)
    private String verificationDocUrls;

    @Column(name = "id_card_front_url", length = 500)
    private String idCardFrontUrl;

    @Column(name = "id_card_back_url", length = 500)
    private String idCardBackUrl;

    @Column(name = "portrait_url", length = 500)
    private String portraitUrl;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    @Builder.Default
    private CustomerStatus status = CustomerStatus.ACTIVE;

    @Column(name = "notes", columnDefinition = "NVARCHAR(500)")
    private String notes;

    public void setVerificationDocType(String verificationDocType) {
        this.verificationDocType = VerificationDocumentPolicy.normalizeDocCode(verificationDocType);
    }

    public void setVerificationDocType(VerificationDocumentType verificationDocType) {
        this.verificationDocType = verificationDocType != null ? verificationDocType.name() : null;
    }

    public static class CustomerEntityBuilder {
        public CustomerEntityBuilder verificationDocType(String verificationDocType) {
            this.verificationDocType = VerificationDocumentPolicy.normalizeDocCode(verificationDocType);
            return this;
        }

        public CustomerEntityBuilder verificationDocType(VerificationDocumentType verificationDocType) {
            this.verificationDocType = verificationDocType != null ? verificationDocType.name() : null;
            return this;
        }
    }

    public List<String> getVerificationDocUrlList() {
        if (verificationDocUrls == null || verificationDocUrls.isBlank()) {
            return Collections.emptyList();
        }
        return Arrays.stream(verificationDocUrls.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }
}
