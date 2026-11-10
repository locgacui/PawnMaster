package com.pawnmaster.api.modules.branch;

import com.pawnmaster.api.modules.branch.dto.BranchCreateRequest;
import com.pawnmaster.api.modules.branch.dto.BranchResponse;
import com.pawnmaster.api.modules.branch.dto.BranchUpdateRequest;
import com.pawnmaster.api.modules.branch.dto.DistrictResponse;
import com.pawnmaster.api.modules.branch.dto.ProvinceResponse;
import com.pawnmaster.api.modules.branch.dto.WardResponse;

import java.util.List;

public interface BranchService {

    BranchResponse createBranch(BranchCreateRequest request);

    BranchResponse updateBranch(Long id, BranchUpdateRequest request);

    BranchResponse getBranchById(Long id);

    BranchResponse getBranchByCode(String branchCode);

    List<BranchResponse> getAllBranches();

    List<BranchResponse> getActiveBranches();

    List<BranchResponse> getBranchesByProvince(String provinceCode);

    List<BranchResponse> getBranchesByProvinceAndDistrict(String provinceCode, String districtCode);

    void deleteBranch(Long id);

    List<ProvinceResponse> getAllProvinces();

    List<DistrictResponse> getDistrictsByProvince(String provinceCode);

    List<WardResponse> getWardsByDistrict(String districtCode);
}
