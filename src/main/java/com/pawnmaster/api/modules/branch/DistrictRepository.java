package com.pawnmaster.api.modules.branch;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DistrictRepository extends JpaRepository<DistrictEntity, String> {

    List<DistrictEntity> findByProvinceCodeOrderByNameAsc(String provinceCode);

    List<DistrictEntity> findByProvinceCodeOrderByCodeAsc(String provinceCode);
}
