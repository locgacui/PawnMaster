package com.pawnmaster.api.modules.branch;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BranchRepository extends JpaRepository<BranchEntity, Long> {

    Optional<BranchEntity> findByIdAndDeletedFalse(Long id);

    Optional<BranchEntity> findByBranchCodeAndDeletedFalse(String branchCode);

    Optional<BranchEntity> findByBranchCode(String branchCode);

    boolean existsByBranchCodeAndDeletedFalse(String branchCode);

    boolean existsByBranchCode(String branchCode);

    List<BranchEntity> findAllByDeletedFalseOrderByIdAsc();

    List<BranchEntity> findAllByDeletedFalseOrderByCreatedAtDesc();

    List<BranchEntity> findByActiveTrueAndDeletedFalse();

    List<BranchEntity> findByActiveTrueAndDeletedFalseOrderByBranchNameAsc();

    List<BranchEntity> findByProvinceCodeAndDeletedFalse(String provinceCode);

    List<BranchEntity> findByProvinceCodeAndDistrictCodeAndDeletedFalse(String provinceCode, String districtCode);
}
