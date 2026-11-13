package com.pawnmaster.api.modules.bank;

import com.pawnmaster.api.common.BaseResponse;
import com.pawnmaster.api.modules.bank.dto.BankResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Exposes the active Vietnamese bank directory for contract disbursement selection.
 */
@RestController
@RequestMapping("/api/v1/banks")
@RequiredArgsConstructor
public class BankController {

    private final BankRepository bankRepository;

    @GetMapping
    public BaseResponse<List<BankResponse>> getActiveBanks() {
        List<BankResponse> banks = bankRepository
                .findByActiveTrueAndDeletedFalseOrderByDisplayOrderAscShortNameAsc()
                .stream()
                .map(BankResponse::fromEntity)
                .toList();
        return BaseResponse.ok(banks);
    }
}

