package com.pawnmaster.api.modules.branch;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface WardRepository extends JpaRepository<WardEntity, String> {

    List<WardEntity> findByDistrictCodeOrderByNameAsc(String districtCode);

    List<WardEntity> findByDistrictCodeOrderByCodeAsc(String districtCode);
}
