package com.pawnmaster.api.modules.collateral;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CollateralRepository extends JpaRepository<CollateralEntity, Long> {

    Optional<CollateralEntity> findByIdAndDeletedFalse(Long id);

    Optional<CollateralEntity> findByCollateralCodeAndDeletedFalse(String collateralCode);

    List<CollateralEntity> findAllByDeletedFalse();

    @Query("SELECT c FROM CollateralEntity c WHERE c.customer.id = :customerId AND c.deleted = false")
    List<CollateralEntity> findByCustomerIdAndDeletedFalse(@Param("customerId") Long customerId);

    @Query("SELECT c FROM CollateralEntity c WHERE c.branch.id = :branchId AND c.deleted = false")
    List<CollateralEntity> findByBranchIdAndDeletedFalse(@Param("branchId") Long branchId);

    List<CollateralEntity> findByStatusAndDeletedFalse(CollateralStatus status);

    @Query("SELECT c FROM CollateralEntity c WHERE c.branch.id = :branchId AND c.status = :status AND c.deleted = false")
    List<CollateralEntity> findByBranchIdAndStatusAndDeletedFalse(
            @Param("branchId") Long branchId, @Param("status") CollateralStatus status);
}
