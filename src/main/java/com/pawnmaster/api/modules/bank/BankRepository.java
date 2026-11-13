package com.pawnmaster.api.modules.bank;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BankRepository extends JpaRepository<BankEntity, Long> {

    List<BankEntity> findByActiveTrueAndDeletedFalseOrderByDisplayOrderAscShortNameAsc();

    Optional<BankEntity> findByBankCodeIgnoreCaseAndDeletedFalse(String bankCode);
}

