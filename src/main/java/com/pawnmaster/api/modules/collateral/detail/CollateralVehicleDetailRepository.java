package com.pawnmaster.api.modules.collateral.detail;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CollateralVehicleDetailRepository extends JpaRepository<CollateralVehicleDetailEntity, Long> {
}

