package com.pawnmaster.api.modules.branch;

import com.pawnmaster.api.common.BusinessException;
import com.pawnmaster.api.common.ErrorCode;
import com.pawnmaster.api.modules.branch.dto.BranchCreateRequest;
import com.pawnmaster.api.modules.branch.dto.BranchResponse;
import com.pawnmaster.api.modules.branch.dto.BranchUpdateRequest;
import com.pawnmaster.api.modules.branch.dto.DistrictResponse;
import com.pawnmaster.api.modules.branch.dto.ProvinceResponse;
import com.pawnmaster.api.modules.branch.dto.WardResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class BranchServiceImpl implements BranchService {

    private final BranchRepository branchRepository;
    private final ProvinceRepository provinceRepository;
    private final DistrictRepository districtRepository;
    private final WardRepository wardRepository;

    @Override
    @Transactional
    public BranchResponse createBranch(BranchCreateRequest request) {
        String branchCode = request.getBranchCode().trim();

        if (branchRepository.existsByBranchCode(branchCode) || branchRepository.existsByBranchCodeAndDeletedFalse(branchCode)) {
            throw new BusinessException(ErrorCode.BRANCH_CODE_EXISTS, "Mã chi nhánh đã tồn tại: " + branchCode);
        }

        // Validate province if repository contains data
        ProvinceEntity province = null;
        if (provinceRepository != null) {
            Optional<ProvinceEntity> pOpt = provinceRepository.findById(request.getProvinceCode());
            if (pOpt.isPresent()) {
                province = pOpt.get();
            } else if (provinceRepository.count() > 0) {
                // If repository is populated and province code not found, throw error
                throw new BusinessException(ErrorCode.PROVINCE_NOT_FOUND, "Không tìm thấy Tỉnh/Thành phố với mã: " + request.getProvinceCode());
            }
        }

        // Validate district if repository contains data
        DistrictEntity district = null;
        if (districtRepository != null) {
            Optional<DistrictEntity> dOpt = districtRepository.findById(request.getDistrictCode());
            if (dOpt.isPresent()) {
                district = dOpt.get();
            } else if (districtRepository.count() > 0) {
                throw new BusinessException(ErrorCode.DISTRICT_NOT_FOUND, "Không tìm thấy Quận/Huyện với mã: " + request.getDistrictCode());
            }
        }

        WardEntity ward = null;
        if (wardRepository != null && request.getWardCode() != null && !request.getWardCode().isBlank()) {
            ward = wardRepository.findById(request.getWardCode()).orElse(null);
        }

        BranchEntity entity = BranchEntity.builder()
                .branchCode(branchCode)
                .branchName(request.getBranchName().trim())
                .provinceCode(request.getProvinceCode())
                .province(province)
                .districtCode(request.getDistrictCode())
                .district(district)
                .wardCode(request.getWardCode())
                .ward(ward)
                .addressDetail(request.getAddressDetail())
                .phoneNumber(request.getPhoneNumber())
                .capitalLimit(request.getCapitalLimit() != null ? request.getCapitalLimit() : new java.math.BigDecimal("2500000000.00"))
                .currentCashBalance(request.resolveInitialCash())
                .active(true)
                .build();

        BranchEntity saved = branchRepository.save(entity);
        return toResponse(saved);
    }

    @Override
    @Transactional
    public BranchResponse updateBranch(Long id, BranchUpdateRequest request) {
        BranchEntity branch = branchRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.BRANCH_NOT_FOUND, "Không tìm thấy chi nhánh với ID: " + id));

        if (request.getBranchName() != null && !request.getBranchName().isBlank()) {
            branch.setBranchName(request.getBranchName().trim());
        }
        if (request.getAddressDetail() != null) {
            branch.setAddressDetail(request.getAddressDetail().trim());
        }
        if (request.getPhoneNumber() != null) {
            branch.setPhoneNumber(request.getPhoneNumber().trim());
        }
        if (request.getCapitalLimit() != null) {
            branch.setCapitalLimit(request.getCapitalLimit());
        }
        if (request.getCurrentCashBalance() != null) {
            branch.setCurrentCashBalance(request.getCurrentCashBalance());
        }
        if (request.getActive() != null) {
            branch.setActive(request.getActive());
        }
        if (request.getProvinceCode() != null && !request.getProvinceCode().isBlank()) {
            branch.setProvinceCode(request.getProvinceCode());
            if (provinceRepository != null) {
                branch.setProvince(provinceRepository.findById(request.getProvinceCode()).orElse(null));
            }
        }
        if (request.getDistrictCode() != null && !request.getDistrictCode().isBlank()) {
            branch.setDistrictCode(request.getDistrictCode());
            if (districtRepository != null) {
                branch.setDistrict(districtRepository.findById(request.getDistrictCode()).orElse(null));
            }
        }
        if (request.getWardCode() != null) {
            branch.setWardCode(request.getWardCode());
            if (wardRepository != null && !request.getWardCode().isBlank()) {
                branch.setWard(wardRepository.findById(request.getWardCode()).orElse(null));
            }
        }

        BranchEntity updated = branchRepository.save(branch);
        return toResponse(updated);
    }

    @Override
    @Transactional(readOnly = true)
    public BranchResponse getBranchById(Long id) {
        BranchEntity branch = branchRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.BRANCH_NOT_FOUND, "Không tìm thấy chi nhánh với ID: " + id));
        return toResponse(branch);
    }

    @Override
    @Transactional(readOnly = true)
    public BranchResponse getBranchByCode(String branchCode) {
        BranchEntity branch = branchRepository.findByBranchCodeAndDeletedFalse(branchCode)
                .orElseThrow(() -> new BusinessException(ErrorCode.BRANCH_NOT_FOUND, "Không tìm thấy chi nhánh với mã: " + branchCode));
        return toResponse(branch);
    }

    @Override
    @Transactional(readOnly = true)
    public List<BranchResponse> getAllBranches() {
        return branchRepository.findAllByDeletedFalseOrderByCreatedAtDesc()
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<BranchResponse> getActiveBranches() {
        return branchRepository.findByActiveTrueAndDeletedFalseOrderByBranchNameAsc()
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<BranchResponse> getBranchesByProvince(String provinceCode) {
        return branchRepository.findByProvinceCodeAndDeletedFalse(provinceCode)
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<BranchResponse> getBranchesByProvinceAndDistrict(String provinceCode, String districtCode) {
        return branchRepository.findByProvinceCodeAndDistrictCodeAndDeletedFalse(provinceCode, districtCode)
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void deleteBranch(Long id) {
        BranchEntity branch = branchRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.BRANCH_NOT_FOUND, "Không tìm thấy chi nhánh với ID: " + id));
        branch.setDeleted(true);
        branch.setActive(false);
        branchRepository.save(branch);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProvinceResponse> getAllProvinces() {
        return provinceRepository.findAllByOrderByNameAsc()
                .stream()
                .map(this::toProvinceResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<DistrictResponse> getDistrictsByProvince(String provinceCode) {
        return districtRepository.findByProvinceCodeOrderByNameAsc(provinceCode)
                .stream()
                .map(this::toDistrictResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<WardResponse> getWardsByDistrict(String districtCode) {
        return wardRepository.findByDistrictCodeOrderByNameAsc(districtCode)
                .stream()
                .map(this::toWardResponse)
                .collect(Collectors.toList());
    }

    private BranchResponse toResponse(BranchEntity branch) {
        String provinceName = null;
        if (branch.getProvince() != null) {
            provinceName = resolveDisplayName(branch.getProvince().getFullName(), branch.getProvince().getName());
        } else if (provinceRepository != null && branch.getProvinceCode() != null) {
            provinceName = provinceRepository.findById(branch.getProvinceCode())
                    .map(p -> resolveDisplayName(p.getFullName(), p.getName()))
                    .orElse(null);
        }

        String districtName = null;
        if (branch.getDistrict() != null) {
            districtName = resolveDisplayName(branch.getDistrict().getFullName(), branch.getDistrict().getName());
        } else if (districtRepository != null && branch.getDistrictCode() != null) {
            districtName = districtRepository.findById(branch.getDistrictCode())
                    .map(d -> resolveDisplayName(d.getFullName(), d.getName()))
                    .orElse(null);
        }

        String wardName = null;
        if (branch.getWard() != null) {
            wardName = resolveDisplayName(branch.getWard().getFullName(), branch.getWard().getName());
        } else if (wardRepository != null && branch.getWardCode() != null) {
            wardName = wardRepository.findById(branch.getWardCode())
                    .map(w -> resolveDisplayName(w.getFullName(), w.getName()))
                    .orElse(null);
        }

        List<String> addressParts = new ArrayList<>();
        if (branch.getAddressDetail() != null && !branch.getAddressDetail().isBlank()) {
            addressParts.add(branch.getAddressDetail());
        }
        if (wardName != null && !wardName.isBlank()) {
            addressParts.add(wardName);
        }
        if (districtName != null && !districtName.isBlank()) {
            addressParts.add(districtName);
        }
        if (provinceName != null && !provinceName.isBlank()) {
            addressParts.add(provinceName);
        }
        String fullAddress = String.join(", ", addressParts);

        return BranchResponse.builder()
                .id(branch.getId())
                .branchCode(branch.getBranchCode())
                .branchName(branch.getBranchName())
                .provinceCode(branch.getProvinceCode())
                .provinceName(provinceName)
                .districtCode(branch.getDistrictCode())
                .districtName(districtName)
                .wardCode(branch.getWardCode())
                .wardName(wardName)
                .addressDetail(branch.getAddressDetail())
                .fullAddress(fullAddress)
                .phoneNumber(branch.getPhoneNumber())
                .capitalLimit(branch.getCapitalLimit())
                .currentCashBalance(branch.getCurrentCashBalance())
                .active(branch.isActive())
                .createdAt(branch.getCreatedAt())
                .updatedAt(branch.getUpdatedAt())
                .build();
    }

    private String resolveDisplayName(String fullName, String shortName) {
        if (fullName != null && !fullName.isBlank()) {
            return fullName;
        }
        return shortName;
    }

    private ProvinceResponse toProvinceResponse(ProvinceEntity p) {
        return ProvinceResponse.builder()
                .code(p.getCode())
                .name(p.getName())
                .nameEn(p.getNameEn())
                .fullName(p.getFullName())
                .fullNameEn(p.getFullNameEn())
                .codeName(p.getCodeName())
                .administrativeUnitId(p.getAdministrativeUnitId())
                .administrativeRegionId(p.getAdministrativeRegionId())
                .build();
    }

    private DistrictResponse toDistrictResponse(DistrictEntity d) {
        return DistrictResponse.builder()
                .code(d.getCode())
                .name(d.getName())
                .nameEn(d.getNameEn())
                .fullName(d.getFullName())
                .fullNameEn(d.getFullNameEn())
                .codeName(d.getCodeName())
                .provinceCode(d.getProvinceCode())
                .administrativeUnitId(d.getAdministrativeUnitId())
                .build();
    }

    private WardResponse toWardResponse(WardEntity w) {
        return WardResponse.builder()
                .code(w.getCode())
                .name(w.getName())
                .nameEn(w.getNameEn())
                .fullName(w.getFullName())
                .fullNameEn(w.getFullNameEn())
                .codeName(w.getCodeName())
                .districtCode(w.getDistrictCode())
                .administrativeUnitId(w.getAdministrativeUnitId())
                .build();
    }
}
