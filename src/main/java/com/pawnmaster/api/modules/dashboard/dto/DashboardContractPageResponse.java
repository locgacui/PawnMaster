package com.pawnmaster.api.modules.dashboard.dto;

import com.pawnmaster.api.modules.contract.dto.ContractResponse;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Collections;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DashboardContractPageResponse {

    @Builder.Default
    private List<ContractResponse> items = Collections.emptyList();

    private int page;
    private int displayPage;
    private int size;
    private long totalElements;
    private int totalPages;
    private boolean hasNext;
    private boolean hasPrevious;
    private String activeTab;
    private String keyword;

    public static DashboardContractPageResponse of(
            List<ContractResponse> items,
            int rawPage,
            int rawSize,
            long totalElements,
            String activeTab,
            String keyword
    ) {
        int page = Math.max(0, rawPage);
        int normalizedSize = rawSize <= 0 ? 10 : rawSize;
        int size = Math.max(1, Math.min(50, normalizedSize));
        long safeTotal = Math.max(0L, totalElements);
        int totalPages = safeTotal == 0 ? 0 : (int) Math.ceil((double) safeTotal / size);
        int displayPage = page + 1;
        boolean hasNext = page + 1 < totalPages;
        boolean hasPrevious = page > 0 && safeTotal > 0;
        List<ContractResponse> safeItems = items != null ? items : Collections.emptyList();
        String safeTab = (activeTab != null && !activeTab.isBlank()) ? activeTab : "ALL";

        return DashboardContractPageResponse.builder()
                .items(safeItems)
                .page(page)
                .displayPage(displayPage)
                .size(size)
                .totalElements(safeTotal)
                .totalPages(totalPages)
                .hasNext(hasNext)
                .hasPrevious(hasPrevious)
                .activeTab(safeTab)
                .keyword(keyword)
                .build();
    }

    public static DashboardContractPageResponse empty(int rawSize, String activeTab, String keyword) {
        return of(Collections.emptyList(), 0, rawSize, 0L, activeTab, keyword);
    }
}
