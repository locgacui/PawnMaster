package com.pawnmaster.api.modules.customer.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VerificationDocumentTypeDto {

    private String code;
    private String name;
    private String docCategory;
    private String description;
    private int displayOrder;
    private boolean active;
    private Boolean defaultForGroup;
}

