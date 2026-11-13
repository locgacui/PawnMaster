package com.pawnmaster.api.modules.customer.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OccupationDocumentPolicyItemDto {

    private Long id;
    private String occupationGroup;
    private String docCode;
    private String docName;
    private String docCategory;

    @JsonProperty("isDefault")
    @JsonAlias("default")
    private boolean isDefault;

    private int displayOrder;
    private boolean active;

    @JsonProperty("isDefault")
    public boolean isDefault() {
        return isDefault;
    }
}
