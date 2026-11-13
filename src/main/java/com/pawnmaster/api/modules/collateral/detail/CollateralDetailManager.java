package com.pawnmaster.api.modules.collateral.detail;

import com.pawnmaster.api.common.BusinessException;
import com.pawnmaster.api.common.ErrorCode;
import com.pawnmaster.api.modules.collateral.CollateralCategory;
import com.pawnmaster.api.modules.collateral.CollateralEntity;
import com.pawnmaster.api.modules.collateral.dto.CollateralCreateRequest;
import com.pawnmaster.api.modules.collateral.dto.CollateralResponse;
import com.pawnmaster.api.modules.collateral.dto.CollateralUpdateRequest;
import com.pawnmaster.api.modules.contract.dto.ContractResponse;
import com.pawnmaster.api.modules.contract.dto.QuickContractOriginationRequest;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class CollateralDetailManager {

    private static final int PARENT_SUMMARY_MAX_LENGTH = 255;

    private final CollateralVehicleDetailRepository vehicleDetailRepository;
    private final CollateralTechDetailRepository techDetailRepository;
    private final CollateralGoldDetailRepository goldDetailRepository;
    private final CollateralOtherDetailRepository otherDetailRepository;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CollateralSpecInput {
        private String serialOrFrameNumber;
        private String licensePlate;
        private String vehicleFrameNumber;
        private String vehicleRegNumber;
        private Integer vehicleOdometerKm;
        private String techImeiOrSerial;
        private String techPasscodeStatus;
        private String techAccessories;
        private String goldPurity;
        private BigDecimal goldWeightChi;
        private String goldCertificateCode;
        private String otherAssetSpec;

        public static CollateralSpecInput from(QuickContractOriginationRequest req) {
            return CollateralSpecInput.builder()
                    .serialOrFrameNumber(req.getSerialOrFrameNumber())
                    .licensePlate(req.getLicensePlate())
                    .vehicleFrameNumber(req.getVehicleFrameNumber())
                    .vehicleRegNumber(req.getVehicleRegNumber())
                    .vehicleOdometerKm(req.getVehicleOdometerKm())
                    .techImeiOrSerial(req.getTechImeiOrSerial())
                    .techPasscodeStatus(req.getTechPasscodeStatus())
                    .techAccessories(req.getTechAccessories())
                    .goldPurity(req.getGoldPurity())
                    .goldWeightChi(req.getGoldWeightChi())
                    .goldCertificateCode(req.getGoldCertificateCode())
                    .otherAssetSpec(req.getOtherAssetSpec())
                    .build();
        }

        public static CollateralSpecInput from(CollateralCreateRequest req) {
            return CollateralSpecInput.builder()
                    .serialOrFrameNumber(req.getSerialOrFrameNumber())
                    .licensePlate(req.getLicensePlate())
                    .vehicleFrameNumber(req.getVehicleFrameNumber())
                    .vehicleRegNumber(req.getVehicleRegNumber())
                    .vehicleOdometerKm(req.getVehicleOdometerKm())
                    .techImeiOrSerial(req.getTechImeiOrSerial())
                    .techPasscodeStatus(req.getTechPasscodeStatus())
                    .techAccessories(req.getTechAccessories())
                    .goldPurity(req.getGoldPurity())
                    .goldWeightChi(req.getGoldWeightChi())
                    .goldCertificateCode(req.getGoldCertificateCode())
                    .otherAssetSpec(req.getOtherAssetSpec())
                    .build();
        }

        public static CollateralSpecInput from(CollateralUpdateRequest req) {
            return CollateralSpecInput.builder()
                    .serialOrFrameNumber(req.getSerialOrFrameNumber())
                    .licensePlate(req.getLicensePlate())
                    .vehicleFrameNumber(req.getVehicleFrameNumber())
                    .vehicleRegNumber(req.getVehicleRegNumber())
                    .vehicleOdometerKm(req.getVehicleOdometerKm())
                    .techImeiOrSerial(req.getTechImeiOrSerial())
                    .techPasscodeStatus(req.getTechPasscodeStatus())
                    .techAccessories(req.getTechAccessories())
                    .goldPurity(req.getGoldPurity())
                    .goldWeightChi(req.getGoldWeightChi())
                    .goldCertificateCode(req.getGoldCertificateCode())
                    .otherAssetSpec(req.getOtherAssetSpec())
                    .build();
        }
    }

    public boolean isVehicleCategory(CollateralCategory category) {
        return category == CollateralCategory.MOTORBIKE || category == CollateralCategory.CAR;
    }

    public boolean isTechCategory(CollateralCategory category) {
        return category == CollateralCategory.PHONE
                || category == CollateralCategory.LAPTOP
                || category == CollateralCategory.WATCH;
    }

    public boolean isGoldCategory(CollateralCategory category) {
        return category == CollateralCategory.GOLD_JEWELRY;
    }

    public boolean isOtherCategory(CollateralCategory category) {
        return category == CollateralCategory.OTHER || category == CollateralCategory.REAL_ESTATE;
    }

    public boolean hasAnyDetailField(CollateralSpecInput input) {
        if (input == null) {
            return false;
        }
        return trimToNull(input.getSerialOrFrameNumber()) != null
                || trimToNull(input.getLicensePlate()) != null
                || trimToNull(input.getVehicleFrameNumber()) != null
                || trimToNull(input.getVehicleRegNumber()) != null
                || input.getVehicleOdometerKm() != null
                || trimToNull(input.getTechImeiOrSerial()) != null
                || trimToNull(input.getTechPasscodeStatus()) != null
                || trimToNull(input.getTechAccessories()) != null
                || trimToNull(input.getGoldPurity()) != null
                || input.getGoldWeightChi() != null
                || trimToNull(input.getGoldCertificateCode()) != null
                || trimToNull(input.getOtherAssetSpec()) != null;
    }

    public void validateAndApplyParentSummary(
            CollateralCategory category,
            CollateralSpecInput input,
            CollateralEntity targetParent) {
        if (category == null || targetParent == null) {
            return;
        }
        CollateralSpecInput safeInput = input != null ? input : new CollateralSpecInput();

        if (isVehicleCategory(category)) {
            if (safeInput.getVehicleOdometerKm() != null && safeInput.getVehicleOdometerKm() < 0) {
                throw new BusinessException(ErrorCode.INVALID_REQUEST, "Chỉ số ODO (km) phải >= 0");
            }
            String plate = firstNonBlank(safeInput.getLicensePlate(), targetParent.getLicensePlate());
            String frame = firstNonBlank(
                    safeInput.getVehicleFrameNumber(),
                    safeInput.getSerialOrFrameNumber(),
                    targetParent.getSerialOrFrameNumber());
            targetParent.setLicensePlate(plate);
            targetParent.setSerialOrFrameNumber(truncateSummary(firstNonBlank(frame, plate)));
            return;
        }

        if (isTechCategory(category)) {
            String imei = firstNonBlank(
                    safeInput.getTechImeiOrSerial(),
                    safeInput.getSerialOrFrameNumber(),
                    targetParent.getSerialOrFrameNumber());
            boolean hasAnyTechMeta = trimToNull(safeInput.getTechPasscodeStatus()) != null
                    || trimToNull(safeInput.getTechAccessories()) != null;
            if (imei == null && hasAnyTechMeta) {
                throw new BusinessException(
                        ErrorCode.INVALID_REQUEST,
                        "Thiết bị công nghệ / Đồng hồ bắt buộc nhập Số IMEI hoặc Serial Number");
            }
            targetParent.setLicensePlate(null);
            targetParent.setSerialOrFrameNumber(truncateSummary(imei));
            return;
        }

        if (isGoldCategory(category)) {
            String purity = trimToNull(safeInput.getGoldPurity());
            BigDecimal weight = safeInput.getGoldWeightChi();
            if (purity == null) {
                throw new BusinessException(
                        ErrorCode.INVALID_REQUEST,
                        "Tài sản Vàng / Trang sức bắt buộc chọn Tuổi vàng / Hàm lượng");
            }
            if (weight == null || weight.compareTo(BigDecimal.ZERO) <= 0) {
                throw new BusinessException(
                        ErrorCode.INVALID_REQUEST,
                        "Tài sản Vàng / Trang sức bắt buộc nhập Trọng lượng tịnh (chỉ vàng) > 0");
            }
            String cert = firstNonBlank(safeInput.getGoldCertificateCode(), safeInput.getSerialOrFrameNumber());
            String weightText = weight.stripTrailingZeros().toPlainString() + " chỉ";
            String computedSummary = cert != null ? cert : ("Vàng " + purity + " - " + weightText);
            targetParent.setLicensePlate(null);
            targetParent.setSerialOrFrameNumber(truncateSummary(computedSummary));
            return;
        }

        if (isOtherCategory(category)) {
            String spec = firstNonBlank(
                    safeInput.getOtherAssetSpec(),
                    safeInput.getSerialOrFrameNumber(),
                    targetParent.getSerialOrFrameNumber());
            targetParent.setLicensePlate(null);
            targetParent.setSerialOrFrameNumber(truncateSummary(spec));
        }
    }

    public void saveOrUpdateDetails(CollateralEntity savedParent, CollateralSpecInput input) {
        if (savedParent == null || savedParent.getId() == null || savedParent.getCategory() == null) {
            return;
        }
        Long collateralId = savedParent.getId();
        CollateralCategory category = savedParent.getCategory();
        CollateralSpecInput safeInput = input != null ? input : new CollateralSpecInput();

        if (isVehicleCategory(category)) {
            deleteTechIfPresent(collateralId);
            deleteGoldIfPresent(collateralId);
            deleteOtherIfPresent(collateralId);

            String plate = firstNonBlank(safeInput.getLicensePlate(), savedParent.getLicensePlate());
            String frame = firstNonBlank(
                    safeInput.getVehicleFrameNumber(),
                    safeInput.getSerialOrFrameNumber(),
                    savedParent.getSerialOrFrameNumber());
            String regNum = trimToNull(safeInput.getVehicleRegNumber());
            Integer odo = safeInput.getVehicleOdometerKm();

            if (plate != null || frame != null || regNum != null || odo != null) {
                CollateralVehicleDetailEntity entity = vehicleDetailRepository.findById(collateralId)
                        .orElseGet(() -> CollateralVehicleDetailEntity.builder()
                                .collateralId(collateralId)
                                .collateral(savedParent)
                                .build());
                entity.setCollateral(savedParent);
                entity.setLicensePlate(plate);
                entity.setFrameNumber(frame);
                entity.setRegistrationNumber(regNum);
                entity.setOdometerKm(odo);
                vehicleDetailRepository.save(entity);
            } else {
                deleteVehicleIfPresent(collateralId);
            }
            return;
        }

        if (isTechCategory(category)) {
            deleteVehicleIfPresent(collateralId);
            deleteGoldIfPresent(collateralId);
            deleteOtherIfPresent(collateralId);

            String imei = firstNonBlank(
                    safeInput.getTechImeiOrSerial(),
                    safeInput.getSerialOrFrameNumber(),
                    savedParent.getSerialOrFrameNumber());
            if (imei != null) {
                CollateralTechDetailEntity entity = techDetailRepository.findById(collateralId)
                        .orElseGet(() -> CollateralTechDetailEntity.builder()
                                .collateralId(collateralId)
                                .collateral(savedParent)
                                .build());
                entity.setCollateral(savedParent);
                entity.setImeiOrSerial(imei);
                entity.setPasscodeCloudStatus(trimToNull(safeInput.getTechPasscodeStatus()));
                entity.setAccessories(trimToNull(safeInput.getTechAccessories()));
                techDetailRepository.save(entity);
            } else {
                deleteTechIfPresent(collateralId);
            }
            return;
        }

        if (isGoldCategory(category)) {
            deleteVehicleIfPresent(collateralId);
            deleteTechIfPresent(collateralId);
            deleteOtherIfPresent(collateralId);

            CollateralGoldDetailEntity entity = goldDetailRepository.findById(collateralId)
                    .orElseGet(() -> CollateralGoldDetailEntity.builder()
                            .collateralId(collateralId)
                            .collateral(savedParent)
                            .build());
            entity.setCollateral(savedParent);
            entity.setGoldPurity(trimToNull(safeInput.getGoldPurity()));
            entity.setNetWeightChi(safeInput.getGoldWeightChi());
            entity.setCertificateCode(firstNonBlank(
                    safeInput.getGoldCertificateCode(),
                    safeInput.getSerialOrFrameNumber()));
            goldDetailRepository.save(entity);
            return;
        }

        if (isOtherCategory(category)) {
            deleteVehicleIfPresent(collateralId);
            deleteTechIfPresent(collateralId);
            deleteGoldIfPresent(collateralId);

            String spec = firstNonBlank(
                    safeInput.getOtherAssetSpec(),
                    safeInput.getSerialOrFrameNumber(),
                    savedParent.getSerialOrFrameNumber());
            if (spec != null) {
                CollateralOtherDetailEntity entity = otherDetailRepository.findById(collateralId)
                        .orElseGet(() -> CollateralOtherDetailEntity.builder()
                                .collateralId(collateralId)
                                .collateral(savedParent)
                                .build());
                entity.setCollateral(savedParent);
                entity.setDetailedSpec(spec);
                otherDetailRepository.save(entity);
            } else {
                deleteOtherIfPresent(collateralId);
            }
        }
    }

    public CollateralResponse enrichCollateralResponse(CollateralResponse response) {
        if (response != null) {
            enrichCollateralResponses(List.of(response));
        }
        return response;
    }

    public List<CollateralResponse> enrichCollateralResponses(List<CollateralResponse> responses) {
        if (responses == null || responses.isEmpty()) {
            return responses;
        }

        Set<Long> vehicleIds = new HashSet<>();
        Set<Long> techIds = new HashSet<>();
        Set<Long> goldIds = new HashSet<>();
        Set<Long> otherIds = new HashSet<>();

        for (CollateralResponse item : responses) {
            if (item == null || item.getId() == null || item.getCategory() == null) {
                continue;
            }
            if (isVehicleCategory(item.getCategory())) {
                vehicleIds.add(item.getId());
            } else if (isTechCategory(item.getCategory())) {
                techIds.add(item.getId());
            } else if (isGoldCategory(item.getCategory())) {
                goldIds.add(item.getId());
            } else if (isOtherCategory(item.getCategory())) {
                otherIds.add(item.getId());
            }
        }

        Map<Long, CollateralVehicleDetailEntity> vehicles = loadVehicleMap(vehicleIds);
        Map<Long, CollateralTechDetailEntity> techs = loadTechMap(techIds);
        Map<Long, CollateralGoldDetailEntity> golds = loadGoldMap(goldIds);
        Map<Long, CollateralOtherDetailEntity> others = loadOtherMap(otherIds);

        for (CollateralResponse item : responses) {
            if (item == null || item.getId() == null) {
                continue;
            }
            Long id = item.getId();
            CollateralVehicleDetailEntity vd = vehicles.get(id);
            if (vd != null) {
                if (item.getLicensePlate() == null) {
                    item.setLicensePlate(vd.getLicensePlate());
                }
                item.setVehicleFrameNumber(vd.getFrameNumber());
                item.setVehicleRegNumber(vd.getRegistrationNumber());
                item.setVehicleOdometerKm(vd.getOdometerKm());
            }
            CollateralTechDetailEntity td = techs.get(id);
            if (td != null) {
                item.setTechImeiOrSerial(td.getImeiOrSerial());
                item.setTechPasscodeStatus(td.getPasscodeCloudStatus());
                item.setTechAccessories(td.getAccessories());
            }
            CollateralGoldDetailEntity gd = golds.get(id);
            if (gd != null) {
                item.setGoldPurity(gd.getGoldPurity());
                item.setGoldWeightChi(gd.getNetWeightChi());
                item.setGoldCertificateCode(gd.getCertificateCode());
            }
            CollateralOtherDetailEntity od = others.get(id);
            if (od != null) {
                item.setOtherAssetSpec(od.getDetailedSpec());
            }
        }
        return responses;
    }

    public ContractResponse enrichContractResponse(ContractResponse response) {
        if (response != null) {
            enrichContractResponses(List.of(response));
        }
        return response;
    }

    public List<ContractResponse> enrichContractResponses(List<ContractResponse> responses) {
        if (responses == null || responses.isEmpty()) {
            return responses;
        }

        Set<Long> vehicleIds = new HashSet<>();
        Set<Long> techIds = new HashSet<>();
        Set<Long> goldIds = new HashSet<>();
        Set<Long> otherIds = new HashSet<>();

        for (ContractResponse item : responses) {
            if (item == null || item.getCollateralId() == null || item.getCollateralCategory() == null) {
                continue;
            }
            if (isVehicleCategory(item.getCollateralCategory())) {
                vehicleIds.add(item.getCollateralId());
            } else if (isTechCategory(item.getCollateralCategory())) {
                techIds.add(item.getCollateralId());
            } else if (isGoldCategory(item.getCollateralCategory())) {
                goldIds.add(item.getCollateralId());
            } else if (isOtherCategory(item.getCollateralCategory())) {
                otherIds.add(item.getCollateralId());
            }
        }

        Map<Long, CollateralVehicleDetailEntity> vehicles = loadVehicleMap(vehicleIds);
        Map<Long, CollateralTechDetailEntity> techs = loadTechMap(techIds);
        Map<Long, CollateralGoldDetailEntity> golds = loadGoldMap(goldIds);
        Map<Long, CollateralOtherDetailEntity> others = loadOtherMap(otherIds);

        for (ContractResponse item : responses) {
            if (item == null || item.getCollateralId() == null) {
                continue;
            }
            Long colId = item.getCollateralId();
            CollateralVehicleDetailEntity vd = vehicles.get(colId);
            if (vd != null) {
                if (item.getCollateralLicensePlate() == null) {
                    item.setCollateralLicensePlate(vd.getLicensePlate());
                }
                item.setCollateralVehicleFrameNumber(vd.getFrameNumber());
                item.setCollateralVehicleRegNumber(vd.getRegistrationNumber());
                item.setCollateralVehicleOdometerKm(vd.getOdometerKm());
            }
            CollateralTechDetailEntity td = techs.get(colId);
            if (td != null) {
                item.setCollateralTechImeiOrSerial(td.getImeiOrSerial());
                item.setCollateralTechPasscodeStatus(td.getPasscodeCloudStatus());
                item.setCollateralTechAccessories(td.getAccessories());
            }
            CollateralGoldDetailEntity gd = golds.get(colId);
            if (gd != null) {
                item.setCollateralGoldPurity(gd.getGoldPurity());
                item.setCollateralGoldWeightChi(gd.getNetWeightChi());
                item.setCollateralGoldCertificateCode(gd.getCertificateCode());
            }
            CollateralOtherDetailEntity od = others.get(colId);
            if (od != null) {
                item.setCollateralOtherAssetSpec(od.getDetailedSpec());
            }
        }
        return responses;
    }

    private Map<Long, CollateralVehicleDetailEntity> loadVehicleMap(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<Long, CollateralVehicleDetailEntity> map = new HashMap<>();
        for (CollateralVehicleDetailEntity e : vehicleDetailRepository.findAllById(ids)) {
            if (e != null && e.getCollateralId() != null) {
                map.put(e.getCollateralId(), e);
            }
        }
        return map;
    }

    private Map<Long, CollateralTechDetailEntity> loadTechMap(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<Long, CollateralTechDetailEntity> map = new HashMap<>();
        for (CollateralTechDetailEntity e : techDetailRepository.findAllById(ids)) {
            if (e != null && e.getCollateralId() != null) {
                map.put(e.getCollateralId(), e);
            }
        }
        return map;
    }

    private Map<Long, CollateralGoldDetailEntity> loadGoldMap(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<Long, CollateralGoldDetailEntity> map = new HashMap<>();
        for (CollateralGoldDetailEntity e : goldDetailRepository.findAllById(ids)) {
            if (e != null && e.getCollateralId() != null) {
                map.put(e.getCollateralId(), e);
            }
        }
        return map;
    }

    private Map<Long, CollateralOtherDetailEntity> loadOtherMap(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<Long, CollateralOtherDetailEntity> map = new HashMap<>();
        for (CollateralOtherDetailEntity e : otherDetailRepository.findAllById(ids)) {
            if (e != null && e.getCollateralId() != null) {
                map.put(e.getCollateralId(), e);
            }
        }
        return map;
    }

    private void deleteVehicleIfPresent(Long collateralId) {
        if (vehicleDetailRepository.existsById(collateralId)) {
            vehicleDetailRepository.deleteById(collateralId);
        }
    }

    private void deleteTechIfPresent(Long collateralId) {
        if (techDetailRepository.existsById(collateralId)) {
            techDetailRepository.deleteById(collateralId);
        }
    }

    private void deleteGoldIfPresent(Long collateralId) {
        if (goldDetailRepository.existsById(collateralId)) {
            goldDetailRepository.deleteById(collateralId);
        }
    }

    private void deleteOtherIfPresent(Long collateralId) {
        if (otherDetailRepository.existsById(collateralId)) {
            otherDetailRepository.deleteById(collateralId);
        }
    }

    private static String firstNonBlank(String... candidates) {
        if (candidates == null) {
            return null;
        }
        for (String c : candidates) {
            String trimmed = trimToNull(c);
            if (trimmed != null) {
                return trimmed;
            }
        }
        return null;
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static String truncateSummary(String value) {
        if (value == null || value.length() <= PARENT_SUMMARY_MAX_LENGTH) {
            return value;
        }
        return value.substring(0, PARENT_SUMMARY_MAX_LENGTH);
    }
}
