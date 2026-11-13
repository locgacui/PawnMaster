package com.pawnmaster.api.modules.bank.dto;

import com.pawnmaster.api.modules.bank.BankEntity;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Public read projection for a supported Vietnamese disbursement bank.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BankResponse {

    private Long id;
    private String bankCode;
    private String bin;
    private String shortName;
    private String fullName;
    private String bankGroup;
    private Integer displayOrder;
    private String displayLabel;

    public static BankResponse fromEntity(BankEntity entity) {
        if (entity == null) {
            return null;
        }
        String code = entity.getBankCode() != null ? entity.getBankCode() : "";
        String shortNm = entity.getShortName() != null ? entity.getShortName() : code;
        String fullNm = entity.getFullName() != null ? entity.getFullName() : shortNm;
        String displayLabel = code + " — " + shortNm + " (" + fullNm + ")";
        return BankResponse.builder()
                .id(entity.getId())
                .bankCode(entity.getBankCode())
                .bin(entity.getBin())
                .shortName(entity.getShortName())
                .fullName(entity.getFullName())
                .bankGroup(entity.getBankGroup())
                .displayOrder(entity.getDisplayOrder())
                .displayLabel(displayLabel)
                .build();
    }
}

