package com.pawnmaster.api.modules.customer.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VerificationDocumentMatrixResponse {

    private List<VerificationDocumentTypeDto> documentTypes;
    private Map<String, List<OccupationDocumentPolicyItemDto>> policiesByOccupation;
}

