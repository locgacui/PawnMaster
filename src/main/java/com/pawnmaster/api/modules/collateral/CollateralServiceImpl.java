package com.pawnmaster.api.modules.collateral;

import com.pawnmaster.api.common.BusinessCodeGenerator;
import com.pawnmaster.api.common.BusinessException;
import com.pawnmaster.api.common.ErrorCode;
import com.pawnmaster.api.modules.branch.BranchEntity;
import com.pawnmaster.api.modules.branch.BranchRepository;
import com.pawnmaster.api.modules.collateral.detail.CollateralDetailManager;
import com.pawnmaster.api.modules.collateral.dto.CollateralCreateRequest;
import com.pawnmaster.api.modules.collateral.dto.CollateralResponse;
import com.pawnmaster.api.modules.collateral.dto.CollateralUpdateRequest;
import com.pawnmaster.api.modules.collateral.dto.LtvEvaluationRequest;
import com.pawnmaster.api.modules.collateral.dto.LtvEvaluationResponse;
import com.pawnmaster.api.modules.customer.CustomerEntity;
import com.pawnmaster.api.modules.customer.CustomerOccupationGroup;
import com.pawnmaster.api.modules.customer.CustomerRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@Service
public class CollateralServiceImpl implements CollateralService {

    private final CollateralRepository collateralRepository;
    private final CustomerRepository customerRepository;
    private final BranchRepository branchRepository;
    private final LtvPolicyService ltvPolicyService;
    private CollateralDetailManager collateralDetailManager;

    public CollateralServiceImpl(
            CollateralRepository collateralRepository,
            CustomerRepository customerRepository,
            BranchRepository branchRepository,
            LtvPolicyService ltvPolicyService) {
        this(collateralRepository, customerRepository, branchRepository, ltvPolicyService, null);
    }

    @Autowired
    public CollateralServiceImpl(
            CollateralRepository collateralRepository,
            CustomerRepository customerRepository,
            BranchRepository branchRepository,
            LtvPolicyService ltvPolicyService,
            @Autowired(required = false) CollateralDetailManager collateralDetailManager) {
        this.collateralRepository = collateralRepository;
        this.customerRepository = customerRepository;
        this.branchRepository = branchRepository;
        this.ltvPolicyService = ltvPolicyService;
        this.collateralDetailManager = collateralDetailManager;
    }

    public void setCollateralDetailManager(CollateralDetailManager collateralDetailManager) {
        this.collateralDetailManager = collateralDetailManager;
    }

    @Override
    @Transactional
    public CollateralResponse createCollateral(CollateralCreateRequest request) {
        CustomerEntity customer = customerRepository.findById(request.getCustomerId())
                .orElseThrow(() -> new BusinessException(ErrorCode.CUSTOMER_NOT_FOUND));

        BranchEntity branch = null;
        if (request.getBranchId() != null) {
            branch = branchRepository.findById(request.getBranchId())
                    .orElseThrow(() -> new BusinessException(ErrorCode.BRANCH_NOT_FOUND));
        }

        BigDecimal maxLtv = request.getMaxLtvPercent();
        if (maxLtv == null && customer.getOccupationGroup() != null && request.getCategory() != null) {
            maxLtv = ltvPolicyService.getMaxLtvRatio(customer.getOccupationGroup(), request.getCategory());
        }

        String collateralCode = request.getCollateralCode();
        if (collateralCode == null || collateralCode.isBlank()) {
            Long effectiveBranchId = branch != null ? branch.getId() : request.getBranchId();
            collateralCode = BusinessCodeGenerator.generateCollateralCode(effectiveBranchId, request.getCategory());
        }

        CollateralEntity collateral = CollateralEntity.builder()
                .collateralCode(collateralCode)
                .customer(customer)
                .branch(branch)
                .name(request.getName())
                .category(request.getCategory())
                .brand(request.getBrand())
                .model(request.getModel())
                .serialOrFrameNumber(request.getSerialOrFrameNumber())
                .licensePlate(request.getLicensePlate())
                .conditionDescription(request.getConditionDescription())
                .appraisedValue(request.getAppraisedValue())
                .maxLtvPercent(maxLtv)
                .storageLocation(request.getStorageLocation())
                .imageUrls(request.getImageUrls())
                .status(request.getStatus() != null ? request.getStatus() : CollateralStatus.IN_VAULT)
                .build();

        CollateralDetailManager.CollateralSpecInput specInput = collateralDetailManager != null
                ? CollateralDetailManager.CollateralSpecInput.from(request)
                : null;
        if (collateralDetailManager != null) {
            collateralDetailManager.validateAndApplyParentSummary(request.getCategory(), specInput, collateral);
        }

        CollateralEntity saved = collateralRepository.save(collateral);
        if (collateralDetailManager != null) {
            collateralDetailManager.saveOrUpdateDetails(saved, specInput);
        }
        return enrichSingle(mapToResponse(saved));
    }

    @Override
    @Transactional(readOnly = true)
    public CollateralResponse getCollateralById(Long id) {
        CollateralEntity entity = collateralRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.COLLATERAL_NOT_FOUND));
        return enrichSingle(mapToResponse(entity));
    }

    @Override
    @Transactional(readOnly = true)
    public CollateralResponse getCollateralByCode(String collateralCode) {
        CollateralEntity entity = collateralRepository.findByCollateralCodeAndDeletedFalse(collateralCode)
                .orElseThrow(() -> new BusinessException(ErrorCode.COLLATERAL_NOT_FOUND));
        return enrichSingle(mapToResponse(entity));
    }

    @Override
    @Transactional(readOnly = true)
    public List<CollateralResponse> getAllCollaterals() {
        return enrichList(collateralRepository.findAllByDeletedFalse().stream()
                .map(this::mapToResponse)
                .toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<CollateralResponse> getCollateralsByCustomerId(Long customerId) {
        return enrichList(collateralRepository.findByCustomerIdAndDeletedFalse(customerId).stream()
                .map(this::mapToResponse)
                .toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<CollateralResponse> getCollateralsByBranch(Long branchId) {
        return enrichList(collateralRepository.findByBranchIdAndDeletedFalse(branchId).stream()
                .map(this::mapToResponse)
                .toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<CollateralResponse> getCollateralsByStatus(CollateralStatus status) {
        return enrichList(collateralRepository.findByStatusAndDeletedFalse(status).stream()
                .map(this::mapToResponse)
                .toList());
    }

    @Override
    @Transactional
    public CollateralResponse updateCollateral(Long id, CollateralUpdateRequest request) {
        CollateralEntity entity = collateralRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.COLLATERAL_NOT_FOUND));

        CollateralCategory previousCategory = entity.getCategory();
        if (request.getName() != null)
            entity.setName(request.getName());
        if (request.getCategory() != null)
            entity.setCategory(request.getCategory());
        if (request.getBrand() != null)
            entity.setBrand(request.getBrand());
        if (request.getModel() != null)
            entity.setModel(request.getModel());
        if (request.getSerialOrFrameNumber() != null)
            entity.setSerialOrFrameNumber(request.getSerialOrFrameNumber());
        if (request.getLicensePlate() != null)
            entity.setLicensePlate(request.getLicensePlate());
        if (request.getConditionDescription() != null)
            entity.setConditionDescription(request.getConditionDescription());
        if (request.getAppraisedValue() != null)
            entity.setAppraisedValue(request.getAppraisedValue());
        if (request.getMaxLtvPercent() != null)
            entity.setMaxLtvPercent(request.getMaxLtvPercent());
        if (request.getStorageLocation() != null)
            entity.setStorageLocation(request.getStorageLocation());
        if (request.getImageUrls() != null)
            entity.setImageUrls(request.getImageUrls());
        if (request.getStatus() != null)
            entity.setStatus(request.getStatus());

        if (collateralDetailManager != null) {
            boolean categoryChanged = request.getCategory() != null && !request.getCategory().equals(previousCategory);
            CollateralDetailManager.CollateralSpecInput specInput = CollateralDetailManager.CollateralSpecInput.from(request);
            if (categoryChanged || collateralDetailManager.hasAnyDetailField(specInput)) {
                collateralDetailManager.validateAndApplyParentSummary(entity.getCategory(), specInput, entity);
                collateralDetailManager.saveOrUpdateDetails(entity, specInput);
            }
        }

        return enrichSingle(mapToResponse(entity));
    }

    @Override
    @Transactional
    public void deleteCollateral(Long id) {
        CollateralEntity entity = collateralRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.COLLATERAL_NOT_FOUND));
        entity.setDeleted(true);
    }

    @Override
    public LtvEvaluationResponse evaluateLtv(LtvEvaluationRequest request) {
        CustomerOccupationGroup group = request.getOccupationGroup();
        CollateralCategory category = request.getCategory();
        BigDecimal appraisedValue = request.getAppraisedValue();
        BigDecimal requestedLoan = request.getRequestedLoanAmount();

        if (group == null || category == null || appraisedValue == null) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST,
                    "Nhóm khách hàng, loại tài sản và giá định giá không được để trống");
        }

        BigDecimal maxLtvRatio = ltvPolicyService.getMaxLtvRatio(group, category);
        BigDecimal maxAllowedLoan = ltvPolicyService.calculateMaxLoanAmount(group, category, appraisedValue);
        BigDecimal maxLtvDisplayPercent = maxLtvRatio.multiply(BigDecimal.valueOf(100)).setScale(1,
                RoundingMode.HALF_UP);

        boolean eligible = true;
        String eligibleMessage = "Hạn mức vay khả dụng: " + maxAllowedLoan + " đ (" + maxLtvDisplayPercent
                + "% giá trị định giá)";

        if (requestedLoan != null) {
            if (requestedLoan.compareTo(appraisedValue) > 0) {
                throw new BusinessException(ErrorCode.EXCEEDS_MAX_LTV,
                        "Số tiền vay (" + requestedLoan + " đ) vượt quá trần 100% giá trị định giá tài sản ("
                                + appraisedValue + " đ)");
            }
            if (requestedLoan.compareTo(maxAllowedLoan) > 0) {
                eligible = false;
                eligibleMessage = "Số tiền yêu cầu (" + requestedLoan + " đ) vượt quá hạn mức tối đa cho phép ("
                        + maxAllowedLoan + " đ)";
            }
        }

        return LtvEvaluationResponse.builder()
                .occupationGroup(group)
                .category(category)
                .appraisedValue(appraisedValue)
                .maxLtvPercent(maxLtvRatio)
                .maxAllowedLoanAmount(maxAllowedLoan)
                .requestedLoanAmount(requestedLoan)
                .eligible(eligible)
                .eligibleMessage(eligibleMessage)
                .build();
    }

    private CollateralResponse mapToResponse(CollateralEntity entity) {
        BigDecimal maxAllowedLoan = BigDecimal.ZERO;
        if (entity.getAppraisedValue() != null && entity.getMaxLtvPercent() != null) {
            if (entity.getMaxLtvPercent().compareTo(BigDecimal.ONE) > 0) {
                maxAllowedLoan = entity.getAppraisedValue().multiply(entity.getMaxLtvPercent())
                        .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
            } else {
                maxAllowedLoan = entity.getAppraisedValue().multiply(entity.getMaxLtvPercent()).setScale(2,
                        RoundingMode.HALF_UP);
            }
        }

        return CollateralResponse.builder()
                .id(entity.getId())
                .collateralCode(entity.getCollateralCode())
                .customerId(entity.getCustomerId())
                .customerCode(entity.getCustomer() != null ? entity.getCustomer().getCustomerCode() : null)
                .customerName(entity.getCustomerName())
                .branchId(entity.getBranchId())
                .branchCode(entity.getBranchCode())
                .branchName(entity.getBranchName())
                .name(entity.getName())
                .category(entity.getCategory())
                .brand(entity.getBrand())
                .model(entity.getModel())
                .serialOrFrameNumber(entity.getSerialOrFrameNumber())
                .licensePlate(entity.getLicensePlate())
                .conditionDescription(entity.getConditionDescription())
                .appraisedValue(entity.getAppraisedValue())
                .maxLtvPercent(entity.getMaxLtvPercent())
                .maxAllowedLoanAmount(maxAllowedLoan)
                .storageLocation(entity.getStorageLocation())
                .imageUrls(entity.getImageUrls())
                .status(entity.getStatus())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    private CollateralResponse enrichSingle(CollateralResponse response) {
        if (collateralDetailManager == null || response == null) {
            return response;
        }
        return collateralDetailManager.enrichCollateralResponse(response);
    }

    private List<CollateralResponse> enrichList(List<CollateralResponse> responses) {
        if (collateralDetailManager == null || responses == null || responses.isEmpty()) {
            return responses;
        }
        return collateralDetailManager.enrichCollateralResponses(responses);
    }
}
