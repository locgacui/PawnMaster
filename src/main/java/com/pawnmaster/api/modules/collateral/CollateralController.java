package com.pawnmaster.api.modules.collateral;

import com.pawnmaster.api.common.BaseResponse;
import com.pawnmaster.api.modules.collateral.dto.CollateralCreateRequest;
import com.pawnmaster.api.modules.collateral.dto.CollateralResponse;
import com.pawnmaster.api.modules.collateral.dto.CollateralUpdateRequest;
import com.pawnmaster.api.modules.collateral.dto.LtvEvaluationRequest;
import com.pawnmaster.api.modules.collateral.dto.LtvEvaluationResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/collaterals")
@RequiredArgsConstructor
public class CollateralController {

    private final CollateralService collateralService;

    @GetMapping("/health")
    public BaseResponse<String> healthCheck() {
        return BaseResponse.ok("Collateral module is ready");
    }

    @PostMapping
    public BaseResponse<CollateralResponse> createCollateral(@Valid @RequestBody CollateralCreateRequest request) {
        return BaseResponse.ok("Tạo tài sản thành công", collateralService.createCollateral(request));
    }

    @GetMapping
    public BaseResponse<List<CollateralResponse>> getAllCollaterals(
            @RequestParam(name = "branchId", required = false) Long branchId,
            @RequestParam(name = "status", required = false) CollateralStatus status
    ) {
        if (branchId != null) {
            return BaseResponse.ok(collateralService.getCollateralsByBranch(branchId));
        }
        if (status != null) {
            return BaseResponse.ok(collateralService.getCollateralsByStatus(status));
        }
        return BaseResponse.ok(collateralService.getAllCollaterals());
    }

    @GetMapping("/{id}")
    public BaseResponse<CollateralResponse> getCollateralById(@PathVariable Long id) {
        return BaseResponse.ok(collateralService.getCollateralById(id));
    }

    @GetMapping("/customer/{customerId}")
    public BaseResponse<List<CollateralResponse>> getCollateralsByCustomerId(@PathVariable Long customerId) {
        return BaseResponse.ok(collateralService.getCollateralsByCustomerId(customerId));
    }

    @PutMapping("/{id}")
    public BaseResponse<CollateralResponse> updateCollateral(
            @PathVariable Long id,
            @Valid @RequestBody CollateralUpdateRequest request
    ) {
        return BaseResponse.ok("Cập nhật tài sản thành công", collateralService.updateCollateral(id, request));
    }

    @DeleteMapping("/{id}")
    public BaseResponse<Void> deleteCollateral(@PathVariable Long id) {
        collateralService.deleteCollateral(id);
        return BaseResponse.ok("Xóa tài sản thành công", null);
    }

    @PostMapping("/evaluate-ltv")
    public BaseResponse<LtvEvaluationResponse> evaluateLtv(@Valid @RequestBody LtvEvaluationRequest request) {
        return BaseResponse.ok("Thẩm định LTV thành công", collateralService.evaluateLtv(request));
    }

    @GetMapping("/categories")
    public BaseResponse<List<Map<String, String>>> getCategories() {
        List<Map<String, String>> list = Arrays.stream(CollateralCategory.values())
                .map(c -> Map.of("code", c.name(), "name", c.name()))
                .toList();
        return BaseResponse.ok(list);
    }
}
