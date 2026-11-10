package com.pawnmaster.api.modules.contract.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ContractRejectRequest {

    @NotBlank(message = "Lý do từ chối không được để trống")
    private String rejectionReason;
}
