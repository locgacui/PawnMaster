package com.pawnmaster.api.modules.branch;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProvinceRepository extends JpaRepository<ProvinceEntity, String> {

    List<ProvinceEntity> findAllByOrderByNameAsc();

    List<ProvinceEntity> findAllByOrderByCodeAsc();
}
