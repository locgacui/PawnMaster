package com.pawnmaster.api.modules.branch;

import com.pawnmaster.api.common.BusinessException;
import com.pawnmaster.api.common.ErrorCode;
import com.pawnmaster.api.modules.branch.dto.BranchCreateRequest;
import com.pawnmaster.api.modules.branch.dto.BranchResponse;
import com.pawnmaster.api.modules.branch.dto.BranchUpdateRequest;
import com.pawnmaster.api.modules.branch.dto.DistrictResponse;
import com.pawnmaster.api.modules.branch.dto.ProvinceResponse;
import com.pawnmaster.api.modules.branch.dto.WardResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class BranchServiceTest {

    @Mock
    private BranchRepository branchRepository;

    @Mock
    private ProvinceRepository provinceRepository;

    @Mock
    private DistrictRepository districtRepository;

    @Mock
    private WardRepository wardRepository;

    @InjectMocks
    private BranchServiceImpl branchService;

    private BranchEntity sampleBranch;
    private BranchCreateRequest createRequest;
    private ProvinceEntity sampleProvince;
    private DistrictEntity sampleDistrict;
    private WardEntity sampleWard;

    @BeforeEach
    void setUp() {
        sampleProvince = ProvinceEntity.builder()
                .code("79")
                .name("Thành phố Hồ Chí Minh")
                .fullName("Thành phố Hồ Chí Minh")
                .build();

        sampleDistrict = DistrictEntity.builder()
                .code("760")
                .name("Quận 1")
                .fullName("Quận 1")
                .provinceCode("79")
                .province(sampleProvince)
                .build();

        sampleWard = WardEntity.builder()
                .code("26734")
                .name("Phường Bến Nghé")
                .fullName("Phường Bến Nghé")
                .districtCode("760")
                .district(sampleDistrict)
                .build();

        sampleBranch = BranchEntity.builder()
                .branchCode("CN-HCM-01")
                .branchName("Chi nhánh Quận 1")
                .provinceCode("79")
                .province(sampleProvince)
                .districtCode("760")
                .district(sampleDistrict)
                .wardCode("26734")
                .ward(sampleWard)
                .addressDetail("123 Nguyễn Huệ")
                .phoneNumber("02838222333")
                .capitalLimit(BigDecimal.valueOf(2500000000L))
                .currentCashBalance(BigDecimal.valueOf(500000000L))
                .active(true)
                .build();
        sampleBranch.setId(1L);

        createRequest = BranchCreateRequest.builder()
                .branchCode("CN-HCM-01")
                .branchName("Chi nhánh Quận 1")
                .provinceCode("79")
                .districtCode("760")
                .wardCode("26734")
                .addressDetail("123 Nguyễn Huệ")
                .phoneNumber("02838222333")
                .capitalLimit(BigDecimal.valueOf(2500000000L))
                .initialCashBalance(BigDecimal.valueOf(500000000L))
                .build();
    }

    @Test
    @DisplayName("createBranch - Thành công khi mã chi nhánh chưa tồn tại")
    void createBranch_Success() {
        when(branchRepository.existsByBranchCode("CN-HCM-01")).thenReturn(false);
        when(branchRepository.save(any(BranchEntity.class))).thenAnswer(invocation -> {
            BranchEntity entity = invocation.getArgument(0);
            entity.setId(1L);
            return entity;
        });

        BranchResponse response = branchService.createBranch(createRequest);

        assertNotNull(response);
        assertEquals("CN-HCM-01", response.getBranchCode());
        assertEquals("Chi nhánh Quận 1", response.getBranchName());
        assertEquals(BigDecimal.valueOf(2500000000L), response.getCapitalLimit());
        assertEquals(BigDecimal.valueOf(500000000L), response.getCurrentCashBalance());
        verify(branchRepository, times(1)).save(any(BranchEntity.class));
    }

    @Test
    @DisplayName("createBranch - Ném ngoại lệ khi mã chi nhánh bị trùng")
    void createBranch_DuplicateBranchCode_ThrowsConflictData() {
        when(branchRepository.existsByBranchCode("CN-HCM-01")).thenReturn(true);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> branchService.createBranch(createRequest));

        assertTrue(exception.getErrorCode() == ErrorCode.BRANCH_CODE_EXISTS || exception.getErrorCode() == ErrorCode.CONFLICT_DATA);
        verify(branchRepository, never()).save(any(BranchEntity.class));
    }

    @Test
    @DisplayName("createBranch - Ném ngoại lệ PROVINCE_NOT_FOUND khi mã tỉnh không tồn tại")
    void createBranch_ProvinceNotFound_ThrowsException() {
        createRequest.setProvinceCode("999");
        when(branchRepository.existsByBranchCode("CN-HCM-01")).thenReturn(false);
        when(provinceRepository.findById("999")).thenReturn(Optional.empty());
        when(provinceRepository.count()).thenReturn(63L);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> branchService.createBranch(createRequest));

        assertEquals(ErrorCode.PROVINCE_NOT_FOUND, exception.getErrorCode());
        verify(branchRepository, never()).save(any(BranchEntity.class));
    }

    @Test
    @DisplayName("getBranchById - Thành công khi tồn tại ID chi nhánh")
    void getBranchById_Success() {
        when(branchRepository.findByIdAndDeletedFalse(1L)).thenReturn(Optional.of(sampleBranch));

        BranchResponse response = branchService.getBranchById(1L);

        assertNotNull(response);
        assertEquals(1L, response.getId());
        assertEquals("CN-HCM-01", response.getBranchCode());
    }

    @Test
    @DisplayName("getBranchById - Ném ngoại lệ BRANCH_NOT_FOUND khi ID không tồn tại")
    void getBranchById_NotFound_ThrowsBranchNotFound() {
        when(branchRepository.findByIdAndDeletedFalse(999L)).thenReturn(Optional.empty());

        BusinessException exception = assertThrows(BusinessException.class,
                () -> branchService.getBranchById(999L));

        assertEquals(ErrorCode.BRANCH_NOT_FOUND, exception.getErrorCode());
    }

    @Test
    @DisplayName("findByProvinceCode - Lấy danh sách chi nhánh theo Tỉnh/TP")
    void findByProvinceCode_ReturnsBranches() {
        when(branchRepository.findByProvinceCodeAndDeletedFalse("79"))
                .thenReturn(List.of(sampleBranch));

        List<BranchResponse> list = branchService.getBranchesByProvince("79");

        assertNotNull(list);
        assertEquals(1, list.size());
        assertEquals("79", list.get(0).getProvinceCode());
    }

    @Test
    @DisplayName("findByProvinceAndDistrict - Lấy danh sách chi nhánh theo Quận/Huyện")
    void findByProvinceAndDistrict_ReturnsBranches() {
        when(branchRepository.findByProvinceCodeAndDistrictCodeAndDeletedFalse("79", "760"))
                .thenReturn(List.of(sampleBranch));

        List<BranchResponse> list = branchService.getBranchesByProvinceAndDistrict("79", "760");

        assertNotNull(list);
        assertEquals(1, list.size());
        assertEquals("760", list.get(0).getDistrictCode());
    }

    @Test
    @DisplayName("updateBranch - Cập nhật thông tin chi nhánh thành công")
    void updateBranch_Success() {
        BranchUpdateRequest updateRequest = BranchUpdateRequest.builder()
                .branchName("Chi nhánh Bến Thành")
                .capitalLimit(BigDecimal.valueOf(3000000000L))
                .addressDetail("456 Lê Lợi")
                .build();

        when(branchRepository.findByIdAndDeletedFalse(1L)).thenReturn(Optional.of(sampleBranch));
        when(branchRepository.save(any(BranchEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        BranchResponse response = branchService.updateBranch(1L, updateRequest);

        assertNotNull(response);
        assertEquals("Chi nhánh Bến Thành", response.getBranchName());
        assertEquals(BigDecimal.valueOf(3000000000L), response.getCapitalLimit());
        assertEquals("456 Lê Lợi", response.getAddressDetail());
    }

    @Test
    @DisplayName("deleteBranch - Xóa mềm chi nhánh thành công")
    void deleteBranch_Success() {
        when(branchRepository.findByIdAndDeletedFalse(1L)).thenReturn(Optional.of(sampleBranch));
        when(branchRepository.save(any(BranchEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        branchService.deleteBranch(1L);

        assertTrue(sampleBranch.isDeleted());
        assertFalse(sampleBranch.isActive());
        verify(branchRepository, times(1)).save(sampleBranch);
    }

    @Test
    @DisplayName("administrativeUnits - Tra cứu Tỉnh/Thành, Quận/Huyện, Phường/Xã")
    void administrativeUnits_Success() {
        when(provinceRepository.findAllByOrderByNameAsc()).thenReturn(List.of(sampleProvince));
        when(districtRepository.findByProvinceCodeOrderByNameAsc("79")).thenReturn(List.of(sampleDistrict));
        when(wardRepository.findByDistrictCodeOrderByNameAsc("760")).thenReturn(List.of(sampleWard));

        List<ProvinceResponse> provinces = branchService.getAllProvinces();
        List<DistrictResponse> districts = branchService.getDistrictsByProvince("79");
        List<WardResponse> wards = branchService.getWardsByDistrict("760");

        assertEquals(1, provinces.size());
        assertEquals("79", provinces.get(0).getCode());
        assertEquals(1, districts.size());
        assertEquals("760", districts.get(0).getCode());
        assertEquals(1, wards.size());
        assertEquals("26734", wards.get(0).getCode());
    }
}
