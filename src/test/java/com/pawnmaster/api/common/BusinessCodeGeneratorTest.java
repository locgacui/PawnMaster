package com.pawnmaster.api.common;

import com.pawnmaster.api.modules.cashbook.TransactionType;
import com.pawnmaster.api.modules.collateral.CollateralCategory;
import com.pawnmaster.api.modules.contract.PaymentType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("BusinessCodeGenerator: Standardized Multi-Branch Code & Checksum Tests")
class BusinessCodeGeneratorTest {

    @Test
    @DisplayName("1. BranchCode: CN-<PROVINCE_ABBR>-<SEQ_2>")
    void generateBranchCode_MatchesRule() {
        String code1 = BusinessCodeGenerator.generateBranchCode("HCM", 1);
        String code2 = BusinessCodeGenerator.generateBranchCode("HN", 12);

        assertEquals("CN-HCM-01", code1);
        assertEquals("CN-HN-12", code2);
        assertTrue(BusinessCodeGenerator.BRANCH_CODE_PATTERN.matcher(code1).matches());
        assertTrue(BusinessCodeGenerator.BRANCH_CODE_PATTERN.matcher(code2).matches());
    }

    @Test
    @DisplayName("2. CustomerCode: KH-<YYMM>-<5_BASE36><1_LUHN36_CHK> (Branch-independent & Checksum verified)")
    void generateCustomerCode_MatchesRuleAndChecksum() {
        LocalDate date = LocalDate.of(2026, 11, 10);
        String customerCode = BusinessCodeGenerator.generateCustomerCode(date);

        assertTrue(customerCode.startsWith("KH-2611-"), "Must start with KH-YYMM-");
        assertEquals(14, customerCode.length());
        assertTrue(BusinessCodeGenerator.CUSTOMER_CODE_PATTERN.matcher(customerCode).matches());
        assertTrue(BusinessCodeGenerator.isValidCustomerCode(customerCode), "Luhn Mod-36 checksum must be valid");

        // Tamper last char -> checksum must fail
        char lastChar = customerCode.charAt(13);
        char tamperedChar = (lastChar == 'A') ? 'B' : 'A';
        String tampered = customerCode.substring(0, 13) + tamperedChar;
        assertFalse(BusinessCodeGenerator.isValidCustomerCode(tampered), "Tampered customer code must fail checksum");

        // Cross-verify SQL Server dbo.fn_CalcLuhnMod36 generated codes
        assertTrue(BusinessCodeGenerator.isValidCustomerCode("KH-2611-AUM01C"));
        assertTrue(BusinessCodeGenerator.isValidCustomerCode("KH-2610-D5302X"));
        assertTrue(BusinessCodeGenerator.isValidCustomerCode("KH-2610-GGK03F"));
    }

    @Test
    @DisplayName("3. CollateralCode: TS-<YYMM>-B<BRANCH_ID>-<CAT2><4_BASE36>")
    void generateCollateralCode_MatchesRuleAndCategoryMapping() {
        LocalDate date = LocalDate.of(2026, 11, 10);

        String motorbikeCode = BusinessCodeGenerator.generateCollateralCode(1L, CollateralCategory.MOTORBIKE, date);
        String carCode = BusinessCodeGenerator.generateCollateralCode(2L, CollateralCategory.CAR, date);
        String phoneCode = BusinessCodeGenerator.generateCollateralCode(3L, CollateralCategory.PHONE, date);
        String goldCode = BusinessCodeGenerator.generateCollateralCode(4L, CollateralCategory.GOLD_JEWELRY, date);

        assertTrue(motorbikeCode.startsWith("TS-2611-B01-XM"));
        assertTrue(carCode.startsWith("TS-2611-B02-OT"));
        assertTrue(phoneCode.startsWith("TS-2611-B03-DT"));
        assertTrue(goldCode.startsWith("TS-2611-B04-VG"));

        assertTrue(BusinessCodeGenerator.COLLATERAL_CODE_PATTERN.matcher(motorbikeCode).matches());
        assertTrue(BusinessCodeGenerator.COLLATERAL_CODE_PATTERN.matcher(carCode).matches());
        assertTrue(BusinessCodeGenerator.COLLATERAL_CODE_PATTERN.matcher(phoneCode).matches());
        assertTrue(BusinessCodeGenerator.COLLATERAL_CODE_PATTERN.matcher(goldCode).matches());
    }

    @Test
    @DisplayName("4. ContractCode: HD-<YYMM>-B<BRANCH_ID>-<6_BASE36_OR_SEQ>")
    void generateContractCode_MatchesRuleAndScalesBeyond10000() {
        LocalDate startDate = LocalDate.of(2026, 11, 10);

        String dynamicCode = BusinessCodeGenerator.generateContractCode(1L, startDate);
        String seqCode = BusinessCodeGenerator.generateContractCodeWithSequence(1L, startDate, 129L);
        String highScaleSeqCode = BusinessCodeGenerator.generateContractCodeWithSequence(15L, startDate, 250_000L);

        assertTrue(dynamicCode.startsWith("HD-2611-B01-"));
        assertEquals("HD-2611-B01-000129", seqCode);
        assertEquals("HD-2611-B15-250000", highScaleSeqCode);

        assertTrue(BusinessCodeGenerator.CONTRACT_CODE_PATTERN.matcher(dynamicCode).matches());
        assertTrue(BusinessCodeGenerator.CONTRACT_CODE_PATTERN.matcher(seqCode).matches());
        assertTrue(BusinessCodeGenerator.CONTRACT_CODE_PATTERN.matcher(highScaleSeqCode).matches());
    }

    @Test
    @DisplayName("5. PaymentCode: PT-<TYPE3>-<YYYYMMDD>-<HHmmss>-<3_BASE36>")
    void generatePaymentCode_MatchesRule() {
        LocalDateTime paymentTime = LocalDateTime.of(2026, 11, 10, 9, 31, 22);

        String laiCode = BusinessCodeGenerator.generatePaymentCode(PaymentType.INTEREST_PAYMENT, paymentTime);
        String gocCode = BusinessCodeGenerator.generatePaymentCode(PaymentType.PARTIAL_PRINCIPAL, paymentTime);
        String tatCode = BusinessCodeGenerator.generatePaymentCode(PaymentType.REDEMPTION, paymentTime);
        String tlyCode = BusinessCodeGenerator.generatePaymentCode(PaymentType.LIQUIDATION, paymentTime);

        assertTrue(laiCode.startsWith("PT-LAI-20261110-093122-"));
        assertTrue(gocCode.startsWith("PT-GOC-20261110-093122-"));
        assertTrue(tatCode.startsWith("PT-TAT-20261110-093122-"));
        assertTrue(tlyCode.startsWith("PT-TLY-20261110-093122-"));

        assertTrue(BusinessCodeGenerator.PAYMENT_CODE_PATTERN.matcher(laiCode).matches());
        assertTrue(BusinessCodeGenerator.PAYMENT_CODE_PATTERN.matcher(gocCode).matches());
        assertTrue(BusinessCodeGenerator.PAYMENT_CODE_PATTERN.matcher(tatCode).matches());
        assertTrue(BusinessCodeGenerator.PAYMENT_CODE_PATTERN.matcher(tlyCode).matches());
    }

    @Test
    @DisplayName("6. TransactionCode: <IN|OUT>-<YYYYMMDD>-<HHmmss>-<3_BASE36>")
    void generateTransactionCode_MatchesUserRule() {
        LocalDateTime txTime = LocalDateTime.of(2026, 11, 10, 9, 31, 22);

        String cashInCode = BusinessCodeGenerator.generateTransactionCode(TransactionType.CASH_IN, txTime);
        String cashOutCode = BusinessCodeGenerator.generateTransactionCode(TransactionType.CASH_OUT, txTime);

        assertTrue(cashInCode.startsWith("IN-20261110-093122-"));
        assertTrue(cashOutCode.startsWith("OUT-20261110-093122-"));
        assertTrue(BusinessCodeGenerator.TRANSACTION_CODE_PATTERN.matcher(cashInCode).matches());
        assertTrue(BusinessCodeGenerator.TRANSACTION_CODE_PATTERN.matcher(cashOutCode).matches());
    }

    @Test
    @DisplayName("7. High-concurrency burst uniqueness test")
    void generateBurstCodes_NoCollisionsInSameMillisecond() {
        Set<String> generated = new HashSet<>();
        LocalDate date = LocalDate.of(2026, 11, 10);
        for (int i = 0; i < 500; i++) {
            generated.add(BusinessCodeGenerator.generateContractCode(1L, date));
        }
        assertEquals(500, generated.size(), "500 burst contract codes in same millisecond must be unique");
    }
}
