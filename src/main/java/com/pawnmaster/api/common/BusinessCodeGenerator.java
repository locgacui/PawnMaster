package com.pawnmaster.api.common;

import com.pawnmaster.api.modules.cashbook.TransactionType;
import com.pawnmaster.api.modules.collateral.CollateralCategory;
import com.pawnmaster.api.modules.contract.PaymentType;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;

/**
 * Centralized Business Code Generator & Validator for PawnMaster Multi-Branch Core.
 *
 * <p>Implements the standardized, B-Tree index-friendly code rules documented in
 * {@code docs/CODE_GENERATION_RULES.md} and {@code dbo.business_code_rules}:</p>
 * <ul>
 *   <li><b>Branch (`branches.branch_code`)</b>: {@code CN-<PROVINCE_ABBR>-<SEQ_2>} (e.g., {@code CN-HCM-01})</li>
 *   <li><b>Customer (`customers.customer_code`)</b>: {@code KH-<YYMM>-<5_BASE36><1_LUHN36_CHK>} (e.g., {@code KH-2611-8F3K9C}) — Branch-independent</li>
 *   <li><b>Collateral (`collaterals.collateral_code`)</b>: {@code TS-<YYMM>-B<BRANCH_ID>-<CAT2><4_BASE36>} (e.g., {@code TS-2611-B01-XM8F2A})</li>
 *   <li><b>Contract (`pawn_contracts.contract_code`)</b>: {@code HD-<YYMM>-B<BRANCH_ID>-<6_BASE36_OR_SEQ>} (e.g., {@code HD-2611-B01-000129})</li>
 *   <li><b>Interest Payment (`interest_payments.payment_code`)</b>: {@code PT-<TYPE3>-<YYYYMMDD>-<HHmmss>-<3_BASE36>} (e.g., {@code PT-LAI-20261110-093122-8FA})</li>
 *   <li><b>Cashbook (`cashbook_transactions.transaction_code`)</b>: {@code <IN|OUT>-<YYYYMMDD>-<HHmmss>-<3_BASE36>} (e.g., {@code IN-20261110-093122-8FA})</li>
 * </ul>
 */
@Component
public class BusinessCodeGenerator {

    private static final String BASE36_ALPHABET = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ";
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final AtomicInteger COUNTER = new AtomicInteger(SECURE_RANDOM.nextInt(36 * 36));

    private static final DateTimeFormatter YYMM_FORMATTER = DateTimeFormatter.ofPattern("yyMM");
    private static final DateTimeFormatter DATE_PART_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter TIME_PART_FORMATTER = DateTimeFormatter.ofPattern("HHmmss");

    public static final Pattern BRANCH_CODE_PATTERN =
            Pattern.compile("^CN-[A-Z]{2,4}-\\d{2,3}$");

    public static final Pattern CUSTOMER_CODE_PATTERN =
            Pattern.compile("^KH-\\d{4}-[0-9A-Z]{6}$");

    public static final Pattern COLLATERAL_CODE_PATTERN =
            Pattern.compile("^TS-\\d{4}-B\\d{2,3}-(XM|OT|DT|LT|VG|DH|BD|KH)[0-9A-Z]{4}$");

    public static final Pattern CONTRACT_CODE_PATTERN =
            Pattern.compile("^HD-\\d{4}-B\\d{2,3}-[0-9A-Z]{6}$");

    public static final Pattern PAYMENT_CODE_PATTERN =
            Pattern.compile("^PT-(LAI|GOC|TAT|TLY)-\\d{8}-\\d{6}-[0-9A-Z]{3}$");

    public static final Pattern TRANSACTION_CODE_PATTERN =
            Pattern.compile("^(IN|OUT)-\\d{8}-\\d{6}-[0-9A-Z]{3}$");

    // =========================================================================
    // 1. BRANCH CODE: CN-<PROVINCE_ABBR>-<SEQ_2>
    // =========================================================================

    public static String generateBranchCode(String provinceAbbr, int sequence) {
        String cleanAbbr = (provinceAbbr == null || provinceAbbr.isBlank())
                ? "VN"
                : provinceAbbr.trim().toUpperCase().replaceAll("[^A-Z]", "");
        if (cleanAbbr.length() < 2) {
            cleanAbbr = cleanAbbr + "X";
        } else if (cleanAbbr.length() > 4) {
            cleanAbbr = cleanAbbr.substring(0, 4);
        }
        int safeSeq = Math.max(1, sequence);
        return String.format("CN-%s-%02d", cleanAbbr, safeSeq);
    }

    // =========================================================================
    // 2. CUSTOMER CODE: KH-<YYMM>-<5_BASE36><1_LUHN36_CHECKSUM> (Chain-wide)
    // =========================================================================

    public static String generateCustomerCode() {
        return generateCustomerCode(LocalDate.now());
    }

    public static String generateCustomerCode(LocalDate date) {
        LocalDate effectiveDate = date != null ? date : LocalDate.now();
        String yymm = effectiveDate.format(YYMM_FORMATTER);
        String payload5 = generateTimeOrderedBase36(5);
        char checksum = computeLuhnMod36Checksum("KH" + yymm + payload5);
        return "KH-" + yymm + "-" + payload5 + checksum;
    }

    /**
     * Validates both the structural pattern and the Luhn Mod-36 checksum character
     * of a standardized {@code customer_code}.
     */
    public static boolean isValidCustomerCode(String customerCode) {
        if (customerCode == null || !CUSTOMER_CODE_PATTERN.matcher(customerCode).matches()) {
            return false;
        }
        String yymm = customerCode.substring(3, 7);
        String payload5 = customerCode.substring(8, 13);
        char actualChecksum = customerCode.charAt(13);
        char expectedChecksum = computeLuhnMod36Checksum("KH" + yymm + payload5);
        return actualChecksum == expectedChecksum;
    }

    // =========================================================================
    // 3. COLLATERAL CODE: TS-<YYMM>-B<BRANCH_ID>-<CAT2><4_BASE36>
    // =========================================================================

    public static String generateCollateralCode(Long branchId, CollateralCategory category) {
        return generateCollateralCode(branchId, category, LocalDate.now());
    }

    public static String generateCollateralCode(Long branchId, CollateralCategory category, LocalDate date) {
        LocalDate effectiveDate = date != null ? date : LocalDate.now();
        String yymm = effectiveDate.format(YYMM_FORMATTER);
        String branchTag = formatBranchTag(branchId);
        String catCode = mapCollateralCategoryCode(category);
        String suffix4 = generateTimeOrderedBase36(4);
        return "TS-" + yymm + "-" + branchTag + "-" + catCode + suffix4;
    }

    public static String mapCollateralCategoryCode(CollateralCategory category) {
        if (category == null) {
            return "KH";
        }
        return switch (category) {
            case MOTORBIKE -> "XM";
            case CAR -> "OT";
            case PHONE -> "DT";
            case LAPTOP -> "LT";
            case GOLD_JEWELRY -> "VG";
            case WATCH -> "DH";
            case REAL_ESTATE -> "BD";
            case OTHER -> "KH";
        };
    }

    // =========================================================================
    // 4. CONTRACT CODE: HD-<YYMM>-B<BRANCH_ID>-<6_BASE36_OR_SEQ>
    // =========================================================================

    public static String generateContractCode(Long branchId) {
        return generateContractCode(branchId, LocalDate.now());
    }

    public static String generateContractCode(Long branchId, LocalDate startDate) {
        LocalDate effectiveDate = startDate != null ? startDate : LocalDate.now();
        String yymm = effectiveDate.format(YYMM_FORMATTER);
        String branchTag = formatBranchTag(branchId);
        String suffix6 = generateTimeOrderedBase36(6);
        return "HD-" + yymm + "-" + branchTag + "-" + suffix6;
    }

    public static String generateContractCodeWithSequence(Long branchId, LocalDate startDate, long sequenceNumber) {
        LocalDate effectiveDate = startDate != null ? startDate : LocalDate.now();
        String yymm = effectiveDate.format(YYMM_FORMATTER);
        String branchTag = formatBranchTag(branchId);
        long safeSeq = Math.max(1L, sequenceNumber);
        String suffix6 = safeSeq <= 999_999L
                ? String.format("%06d", safeSeq)
                : toBase36Padded(safeSeq, 6);
        return "HD-" + yymm + "-" + branchTag + "-" + suffix6;
    }

    // =========================================================================
    // 5. INTEREST PAYMENT CODE: PT-<TYPE3>-<YYYYMMDD>-<HHmmss>-<3_BASE36>
    // =========================================================================

    public static String generatePaymentCode(PaymentType paymentType) {
        return generatePaymentCode(paymentType, LocalDateTime.now());
    }

    public static String generatePaymentCode(PaymentType paymentType, LocalDateTime paymentDate) {
        LocalDateTime effectiveTime = paymentDate != null ? paymentDate : LocalDateTime.now();
        String typeCode = mapPaymentTypeCode(paymentType);
        String datePart = effectiveTime.format(DATE_PART_FORMATTER);
        String timePart = effectiveTime.format(TIME_PART_FORMATTER);
        String rand3 = generateRandomBase36(3);
        return "PT-" + typeCode + "-" + datePart + "-" + timePart + "-" + rand3;
    }

    public static String mapPaymentTypeCode(PaymentType paymentType) {
        if (paymentType == null) {
            return "LAI";
        }
        return switch (paymentType) {
            case INTEREST_PAYMENT -> "LAI";
            case PARTIAL_PRINCIPAL -> "GOC";
            case REDEMPTION -> "TAT";
            case LIQUIDATION -> "TLY";
        };
    }

    // =========================================================================
    // 6. CASHBOOK TRANSACTION CODE: <IN|OUT>-<YYYYMMDD>-<HHmmss>-<3_BASE36>
    // =========================================================================

    public static String generateTransactionCode(TransactionType transactionType) {
        return generateTransactionCode(transactionType, LocalDateTime.now());
    }

    public static String generateTransactionCode(TransactionType transactionType, LocalDateTime transactionDate) {
        LocalDateTime effectiveTime = transactionDate != null ? transactionDate : LocalDateTime.now();
        String prefix = (transactionType == TransactionType.CASH_OUT) ? "OUT" : "IN";
        String datePart = effectiveTime.format(DATE_PART_FORMATTER);
        String timePart = effectiveTime.format(TIME_PART_FORMATTER);
        String rand3 = generateRandomBase36(3);
        return prefix + "-" + datePart + "-" + timePart + "-" + rand3;
    }

    // =========================================================================
    // HELPER & CHECKSUM ALGORITHMS
    // =========================================================================

    public static String formatBranchTag(Long branchId) {
        long safeBranchId = (branchId != null && branchId > 0) ? branchId : 1L;
        if (safeBranchId < 100) {
            return String.format("B%02d", safeBranchId);
        }
        return String.format("B%03d", safeBranchId % 1000);
    }

    /**
     * Computes a Luhn Mod-36 check character (0-9, A-Z) for typo & integrity detection.
     */
    public static char computeLuhnMod36Checksum(String input) {
        if (input == null || input.isEmpty()) {
            return '0';
        }
        String normalized = input.toUpperCase().replaceAll("[^0-9A-Z]", "");
        int factor = 2;
        int sum = 0;
        int n = BASE36_ALPHABET.length(); // 36

        for (int i = normalized.length() - 1; i >= 0; i--) {
            int codePoint = BASE36_ALPHABET.indexOf(normalized.charAt(i));
            if (codePoint < 0) {
                continue;
            }
            int addend = factor * codePoint;
            factor = (factor == 2) ? 1 : 2;
            addend = (addend / n) + (addend % n);
            sum += addend;
        }
        int remainder = sum % n;
        int checkCodePoint = (n - remainder) % n;
        return BASE36_ALPHABET.charAt(checkCodePoint);
    }

    /**
     * Generates a time-ordered Base36 token combining high-order intra-day time progression
     * with an atomic counter and cryptographic randomness so codes sort monotonically within the same day/month.
     */
    private static String generateTimeOrderedBase36(int length) {
        long nowMillis = System.currentTimeMillis();
        // Seconds within a 31-day month cycle (~2.67M seconds) fits in 4 Base36 chars (36^4 = 1,679,616)
        long timeComponent = (nowMillis / 1000L) % 1_679_616L;
        int seq = Math.floorMod(COUNTER.incrementAndGet(), 36 * 36);
        int rand = SECURE_RANDOM.nextInt(36);

        long combined = (timeComponent * 36L * 36L) + seq;
        if (length <= 4) {
            long val = ((nowMillis / 2000L) % (36L * 36L)) * (36L * 36L)
                    + (seq % 36) * 36L + rand;
            return toBase36Padded(val, length);
        }
        if (length == 5) {
            long val = ((nowMillis / 1000L) % (36L * 36L * 36L)) * (36L * 36L) + seq;
            return toBase36Padded(val, 5);
        }
        return toBase36Padded(combined, length);
    }

    public static String generateRandomBase36(int length) {
        StringBuilder sb = new StringBuilder(length);
        int seqChar = Math.floorMod(COUNTER.incrementAndGet(), 36);
        sb.append(BASE36_ALPHABET.charAt(seqChar));
        for (int i = 1; i < length; i++) {
            sb.append(BASE36_ALPHABET.charAt(SECURE_RANDOM.nextInt(BASE36_ALPHABET.length())));
        }
        return sb.toString();
    }

    public static String toBase36Padded(long value, int width) {
        String encoded = Long.toString(Math.abs(value), 36).toUpperCase();
        if (encoded.length() > width) {
            return encoded.substring(encoded.length() - width);
        }
        return "0".repeat(width - encoded.length()) + encoded;
    }
}

