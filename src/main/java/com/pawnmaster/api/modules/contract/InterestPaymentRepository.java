package com.pawnmaster.api.modules.contract;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface InterestPaymentRepository extends JpaRepository<InterestPaymentEntity, Long> {

    Optional<InterestPaymentEntity> findByIdAndDeletedFalse(Long id);

    Optional<InterestPaymentEntity> findByPaymentCodeAndDeletedFalse(String paymentCode);

    @Query("SELECT p FROM InterestPaymentEntity p WHERE p.contract.id = :contractId AND p.deleted = false ORDER BY p.paymentDate DESC")
    List<InterestPaymentEntity> findByContractIdAndDeletedFalseOrderByPaymentDateDesc(@Param("contractId") Long contractId);
}
