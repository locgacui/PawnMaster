package com.pawnmaster.api.modules.collateral;

import com.pawnmaster.api.modules.customer.CustomerOccupationGroup;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface LtvPolicyRepository extends JpaRepository<LtvPolicyEntity, Long> {

    @Query("SELECT p FROM LtvPolicyEntity p WHERE p.occupationGroup = :group AND p.collateralCategory = :category AND p.deleted = false")
    Optional<LtvPolicyEntity> findPolicy(
            @Param("group") CustomerOccupationGroup group,
            @Param("category") CollateralCategory category);

    Optional<LtvPolicyEntity> findByOccupationGroupAndCollateralCategoryAndDeletedFalse(
            CustomerOccupationGroup occupationGroup,
            CollateralCategory collateralCategory);

    List<LtvPolicyEntity> findAllByDeletedFalse();

    boolean existsByOccupationGroupAndCollateralCategoryAndDeletedFalse(
            CustomerOccupationGroup occupationGroup,
            CollateralCategory collateralCategory);
}

