package com.pawnmaster.api.modules.customer;

import com.pawnmaster.api.common.BaseEntity;
import com.pawnmaster.api.modules.branch.BranchEntity;
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
                @Index(name = "idx_customers_occupation", columnList = "occupation_group")
        }
)
public class CustomerEntity extends BaseEntity {

    @Column(name = "customer_code", nullable = false, unique = true, length = 50)
    private String customerCode;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "branch_id", foreignKey = @ForeignKey(name = "fk_customers_branch"))
    private BranchEntity branch;

    @Column(name = "branch_id", insertable = false, updatable = false)
    private Long branchId;

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

    @Column(name = "district_code", length = 20)
    private String districtCode;

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

    @Enumerated(EnumType.STRING)
    @Column(name = "verification_doc_type", length = 50)
    private VerificationDocumentType verificationDocType;

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

    public Long getBranchId() {
        return branch != null ? branch.getId() : branchId;
    }

    public String getBranchCode() {
        return branch != null ? branch.getBranchCode() : null;
    }

    public String getBranchName() {
        return branch != null ? branch.getBranchName() : null;
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
