package com.pawnmaster.api.modules.branch;

import com.pawnmaster.api.common.BaseResponse;
import com.pawnmaster.api.modules.branch.dto.BranchCreateRequest;
import com.pawnmaster.api.modules.branch.dto.BranchResponse;
import com.pawnmaster.api.modules.branch.dto.BranchUpdateRequest;
import com.pawnmaster.api.modules.branch.dto.DistrictResponse;
import com.pawnmaster.api.modules.branch.dto.ProvinceResponse;
import com.pawnmaster.api.modules.branch.dto.WardResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/branches")
@RequiredArgsConstructor
public class BranchController {

    private final BranchService branchService;

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public BaseResponse<BranchResponse> createBranch(@Valid @RequestBody BranchCreateRequest request) {
        return BaseResponse.ok("Tạo chi nhánh thành công", branchService.createBranch(request));
    }

    @GetMapping
    public BaseResponse<List<BranchResponse>> getAllBranches(@RequestParam(name = "activeOnly", required = false, defaultValue = "false") boolean activeOnly) {
        if (activeOnly) {
            return BaseResponse.ok(branchService.getActiveBranches());
        }
        return BaseResponse.ok(branchService.getAllBranches());
    }

    @GetMapping("/active")
    public BaseResponse<List<BranchResponse>> getActiveBranches() {
        return BaseResponse.ok(branchService.getActiveBranches());
    }

    @GetMapping("/{id}")
    public BaseResponse<BranchResponse> getBranchById(@PathVariable Long id) {
        return BaseResponse.ok(branchService.getBranchById(id));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public BaseResponse<BranchResponse> updateBranch(
            @PathVariable Long id,
            @Valid @RequestBody BranchUpdateRequest request
    ) {
        return BaseResponse.ok("Cập nhật chi nhánh thành công", branchService.updateBranch(id, request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public BaseResponse<Void> deleteBranch(@PathVariable Long id) {
        branchService.deleteBranch(id);
        return BaseResponse.ok("Xóa chi nhánh thành công", null);
    }

    @GetMapping("/provinces")
    public BaseResponse<List<ProvinceResponse>> getAllProvinces() {
        return BaseResponse.ok(branchService.getAllProvinces());
    }

    @GetMapping("/provinces/{provinceCode}/districts")
    public BaseResponse<List<DistrictResponse>> getDistrictsByProvincePath(@PathVariable String provinceCode) {
        return BaseResponse.ok(branchService.getDistrictsByProvince(provinceCode));
    }

    @GetMapping("/districts")
    public BaseResponse<List<DistrictResponse>> getDistrictsByProvinceQuery(@RequestParam(name = "provinceCode") String provinceCode) {
        return BaseResponse.ok(branchService.getDistrictsByProvince(provinceCode));
    }

    @GetMapping("/districts/{districtCode}/wards")
    public BaseResponse<List<WardResponse>> getWardsByDistrictPath(@PathVariable String districtCode) {
        return BaseResponse.ok(branchService.getWardsByDistrict(districtCode));
    }

    @GetMapping("/wards")
    public BaseResponse<List<WardResponse>> getWardsByDistrictQuery(@RequestParam(name = "districtCode") String districtCode) {
        return BaseResponse.ok(branchService.getWardsByDistrict(districtCode));
    }
}
