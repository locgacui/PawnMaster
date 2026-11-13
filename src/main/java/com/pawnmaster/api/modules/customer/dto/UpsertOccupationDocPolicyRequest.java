package com.pawnmaster.api.modules.customer.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.pawnmaster.api.modules.customer.CustomerOccupationGroup;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpsertOccupationDocPolicyRequest {

    @NotNull(message = "Nhóm nghề nghiệp không được để trống")
    private CustomerOccupationGroup occupationGroup;

    @NotBlank(message = "Mã chứng từ không được để trống")
    @Pattern(regexp = "^[A-Za-z][A-Za-z0-9_]{1,49}$", message = "Mã chứng từ không hợp lệ")
    private String docCode;

    @JsonProperty("isDefault")
    @JsonAlias("default")
    private Boolean isDefault;

    private Integer displayOrder;

    private Boolean active;
}
