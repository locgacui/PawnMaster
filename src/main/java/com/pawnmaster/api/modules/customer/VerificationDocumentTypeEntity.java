package com.pawnmaster.api.modules.customer;

import com.pawnmaster.api.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
        name = "verification_document_types",
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_ver_doc_types_code", columnNames = {"doc_code"})
        },
        indexes = {
                @Index(name = "idx_ver_doc_types_code", columnList = "doc_code", unique = true),
                @Index(name = "idx_ver_doc_types_active_order", columnList = "is_active, display_order")
        }
)
public class VerificationDocumentTypeEntity extends BaseEntity {

    @Column(name = "doc_code", nullable = false, unique = true, length = 50)
    private String docCode;

    @Column(name = "doc_name", nullable = false, columnDefinition = "NVARCHAR(200)")
    private String docName;

    @Column(name = "doc_category", nullable = false, length = 40)
    @Builder.Default
    private String docCategory = "INCOME";

    @Column(name = "description", columnDefinition = "NVARCHAR(500)")
    private String description;

    @Column(name = "display_order", nullable = false)
    @Builder.Default
    private int displayOrder = 100;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private boolean active = true;
}

