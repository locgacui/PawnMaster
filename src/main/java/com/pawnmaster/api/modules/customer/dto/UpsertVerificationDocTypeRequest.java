package com.pawnmaster.api.modules.customer.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpsertVerificationDocTypeRequest {

    @NotBlank(message = "Mã chứng từ không được để trống")
    @Pattern(regexp = "^[A-Za-z][A-Za-z0-9_]{1,49}$", message = "Mã chứng từ chỉ gồm chữ cái, số và gạch dưới (tối đa 50 ký tự)")
    private String code;

    @NotBlank(message = "Tên chứng từ không được để trống")
    @Size(max = 200, message = "Tên chứng từ tối đa 200 ký tự")
    private String name;

    @Size(max = 40, message = "Nhóm chứng từ tối đa 40 ký tự")
    private String docCategory;

    @Size(max = 500, message = "Mô tả tối đa 500 ký tự")
    private String description;

    private Integer displayOrder;

    private Boolean active;
}

