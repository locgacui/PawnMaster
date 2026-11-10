package com.pawnmaster.api.modules.branch.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BranchResponse {

    private Long id;
    private String branchCode;
    private String branchName;
    private String provinceCode;
    private String provinceName;
    private String districtCode;
    private String districtName;
    private String wardCode;
    private String wardName;
    private String addressDetail;
    private String fullAddress;
    private String phoneNumber;
    private BigDecimal capitalLimit;
    private BigDecimal currentCashBalance;
    private boolean active;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
