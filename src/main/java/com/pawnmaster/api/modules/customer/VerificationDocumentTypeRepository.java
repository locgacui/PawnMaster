package com.pawnmaster.api.modules.customer;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface VerificationDocumentTypeRepository extends JpaRepository<VerificationDocumentTypeEntity, Long> {

    Optional<VerificationDocumentTypeEntity> findByDocCodeIgnoreCaseAndDeletedFalse(String docCode);

    Optional<VerificationDocumentTypeEntity> findByDocCodeIgnoreCase(String docCode);

    List<VerificationDocumentTypeEntity> findAllByActiveTrueAndDeletedFalseOrderByDisplayOrderAscIdAsc();

    List<VerificationDocumentTypeEntity> findAllByDeletedFalseOrderByDisplayOrderAscIdAsc();
}

