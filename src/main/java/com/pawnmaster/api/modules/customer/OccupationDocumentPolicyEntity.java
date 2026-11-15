package com.pawnmaster.api.modules.customer;

import com.pawnmaster.api.common.BaseEntity;
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

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(
        name = "occupation_document_policies",
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_occ_doc_policies_group_code", columnNames = {"occupation_group", "doc_code"})
        },
        indexes = {
                @Index(name = "idx_occ_doc_policies_group_code", columnList = "occupation_group, doc_code", unique = true),
                @Index(name = "idx_occ_doc_policies_lookup", columnList = "occupation_group, is_active, display_order")
        }
)
public class OccupationDocumentPolicyEntity extends BaseEntity {

    @Enumerated(EnumType.STRING)
    @Column(name = "occupation_group", nullable = false, length = 40)
    private CustomerOccupationGroup occupationGroup;

    @Column(name = "doc_code", nullable = false, length = 50)
    private String docCode;

    @Column(name = "is_default", nullable = false)
    @Builder.Default
    private boolean defaultOption = false;

    @Column(name = "display_order", nullable = false)
    @Builder.Default
    private int displayOrder = 100;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private boolean active = true;
}

