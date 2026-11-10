package com.pawnmaster.api.modules.collateral;

import com.pawnmaster.api.modules.collateral.dto.CollateralCreateRequest;
import com.pawnmaster.api.modules.collateral.dto.CollateralResponse;
import com.pawnmaster.api.modules.collateral.dto.CollateralUpdateRequest;
import com.pawnmaster.api.modules.collateral.dto.LtvEvaluationRequest;
import com.pawnmaster.api.modules.collateral.dto.LtvEvaluationResponse;

import java.util.List;

public interface CollateralService {

    CollateralResponse createCollateral(CollateralCreateRequest request);

    CollateralResponse getCollateralById(Long id);

    CollateralResponse getCollateralByCode(String collateralCode);

    List<CollateralResponse> getAllCollaterals();

    List<CollateralResponse> getCollateralsByCustomerId(Long customerId);

    List<CollateralResponse> getCollateralsByBranch(Long branchId);

    List<CollateralResponse> getCollateralsByStatus(CollateralStatus status);

    CollateralResponse updateCollateral(Long id, CollateralUpdateRequest request);

    void deleteCollateral(Long id);

    LtvEvaluationResponse evaluateLtv(LtvEvaluationRequest request);
}
