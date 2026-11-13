package com.pawnmaster.api.modules.customer;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface OccupationDocumentPolicyRepository extends JpaRepository<OccupationDocumentPolicyEntity, Long> {

    List<OccupationDocumentPolicyEntity> findAllByOccupationGroupAndActiveTrueAndDeletedFalseOrderByDisplayOrderAscIdAsc(
            CustomerOccupationGroup occupationGroup);

    List<OccupationDocumentPolicyEntity> findAllByOccupationGroupAndDeletedFalseOrderByDisplayOrderAscIdAsc(
            CustomerOccupationGroup occupationGroup);

    Optional<OccupationDocumentPolicyEntity> findByOccupationGroupAndDocCodeIgnoreCase(
            CustomerOccupationGroup occupationGroup,
            String docCode);

    List<OccupationDocumentPolicyEntity> findAllByActiveTrueAndDeletedFalseOrderByOccupationGroupAscDisplayOrderAscIdAsc();

    List<OccupationDocumentPolicyEntity> findAllByDeletedFalseOrderByOccupationGroupAscDisplayOrderAscIdAsc();
}

