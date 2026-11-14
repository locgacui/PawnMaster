package com.pawnmaster.api.modules.contract;

import com.pawnmaster.api.modules.branch.BranchEntity;
import com.pawnmaster.api.modules.branch.BranchRepository;
import com.pawnmaster.api.modules.cashbook.CashbookService;
import com.pawnmaster.api.modules.cashbook.TransactionCategory;
import com.pawnmaster.api.modules.collateral.CollateralCategory;
import com.pawnmaster.api.modules.collateral.CollateralEntity;
import com.pawnmaster.api.modules.collateral.CollateralRepository;
import com.pawnmaster.api.modules.collateral.CollateralStatus;
import com.pawnmaster.api.modules.collateral.LtvPolicyService;
import com.pawnmaster.api.modules.contract.dto.ContractCreateRequest;
import com.pawnmaster.api.modules.contract.dto.ContractPaymentScheduleResponse;
import com.pawnmaster.api.modules.contract.dto.ContractRedeemRequest;
import com.pawnmaster.api.modules.contract.dto.ContractResponse;
import com.pawnmaster.api.modules.contract.dto.ContractSettlementPreviewResponse;
import com.pawnmaster.api.modules.contract.dto.InterestCollectionRequest;
import com.pawnmaster.api.modules.contract.dto.LiquidationRequest;
import com.pawnmaster.api.modules.customer.CustomerEntity;
import com.pawnmaster.api.modules.customer.CustomerOccupationGroup;
import com.pawnmaster.api.modules.customer.CustomerRepository;
import com.pawnmaster.api.modules.customer.VerificationDocumentPolicy;
import com.pawnmaster.api.modules.user.RoleEnum;
import com.pawnmaster.api.modules.user.UserEntity;
import com.pawnmaster.api.modules.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Contract Payment Schedule Lifecycle & Recalculation Tests")
class ContractPaymentScheduleServiceTest {

    private static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    @Mock
    private ContractRepository contractRepository;

    @Mock
    private CustomerRepository customerRepository;

    @Mock
    private CollateralRepository collateralRepository;

    @Mock
    private BranchRepository branchRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private CashbookService cashbookService;

    @Mock
    private LtvPolicyService ltvPolicyService;

    @Mock
    private VerificationDocumentPolicy verificationDocumentPolicy;

    @Mock
    private InterestPaymentRepository interestPaymentRepository;

    @Mock
    private ContractPaymentScheduleRepository scheduleRepository;

    private ContractScheduleServiceImpl scheduleService;
    private ContractServiceImpl contractService;

    private BranchEntity branch;
    private CustomerEntity customer;
    private CollateralEntity collateral;
    private UserEntity staffUser;
    private UserEntity managerUser;

    private Map<Long, ContractEntity> contractStore;
    private Map<Long, ContractPaymentScheduleEntity> scheduleStore;
    private List<InterestPaymentEntity> paymentStore;
    private AtomicLong contractIdSeq;
    private AtomicLong scheduleIdSeq;
    private AtomicLong paymentIdSeq;

    @BeforeEach
    void setUp() {
        scheduleService = new ContractScheduleServiceImpl(scheduleRepository);
        contractService = new ContractServiceImpl(
                contractRepository,
                customerRepository,
                collateralRepository,
                branchRepository,
                userRepository,
                cashbookService,
                ltvPolicyService,
                verificationDocumentPolicy,
                interestPaymentRepository,
                null,
                null,
                null,
                null,
                null,
                null
        );
        contractService.setContractScheduleService(scheduleService);

        branch = BranchEntity.builder()
                .branchCode("CN-HCM-01")
                .branchName("Chi nhánh Quận 1")
                .provinceCode("79")
                .districtCode("760")
                .currentCashBalance(new BigDecimal("500000000.00"))
                .active(true)
                .build();
        branch.setId(1L);

        customer = CustomerEntity.builder()
                .customerCode("KH-001")
                .fullName("Nguyễn Văn Dũng")
                .occupationGroup(CustomerOccupationGroup.OFFICE_WORKER)
                .verificationDocType("PAYSLIP_BANK_STATEMENT")
                .verificationDocUrls("/uploads/customers/doc.jpg")
                .build();
        customer.setId(100L);

        collateral = CollateralEntity.builder()
                .collateralCode("TS-001")
                .customer(customer)
                .branch(branch)
                .name("Honda SH 150i")
                .category(CollateralCategory.MOTORBIKE)
                .appraisedValue(new BigDecimal("80000000.00"))
                .maxLtvPercent(new BigDecimal("0.90"))
                .status(CollateralStatus.IN_VAULT)
                .build();
        collateral.setId(200L);

        staffUser = UserEntity.builder()
                .username("staff_q1")
                .fullName("NV Quầy 1")
                .role(RoleEnum.STAFF)
                .branch(branch)
                .active(true)
                .build();
        staffUser.setId(1L);

        managerUser = UserEntity.builder()
                .username("manager_q1")
                .fullName("Quản lý Q1")
                .role(RoleEnum.MANAGER)
                .branch(branch)
                .active(true)
                .build();
        managerUser.setId(2L);

        contractStore = new LinkedHashMap<>();
        scheduleStore = new LinkedHashMap<>();
        paymentStore = new ArrayList<>();
        contractIdSeq = new AtomicLong(500L);
        scheduleIdSeq = new AtomicLong(1000L);
        paymentIdSeq = new AtomicLong(2000L);

        lenient().when(branchRepository.findByIdAndDeletedFalse(1L)).thenReturn(Optional.of(branch));
        lenient().when(customerRepository.findByIdAndDeletedFalse(100L)).thenReturn(Optional.of(customer));
        lenient().when(collateralRepository.findByIdAndDeletedFalse(200L)).thenReturn(Optional.of(collateral));
        lenient().when(userRepository.findByIdAndDeletedFalse(1L)).thenReturn(Optional.of(staffUser));
        lenient().when(userRepository.findByIdAndDeletedFalse(2L)).thenReturn(Optional.of(managerUser));

        lenient().when(contractRepository.save(any(ContractEntity.class))).thenAnswer(inv -> {
            ContractEntity c = inv.getArgument(0);
            if (c.getId() == null) {
                c.setId(contractIdSeq.getAndIncrement());
            }
            contractStore.put(c.getId(), c);
            return c;
        });
        lenient().when(contractRepository.findByIdAndDeletedFalse(anyLong())).thenAnswer(inv -> {
            Long id = inv.getArgument(0);
            return Optional.ofNullable(contractStore.get(id));
        });
        lenient().when(contractRepository.findByStatusAndDeletedFalse(any(ContractStatus.class))).thenAnswer(inv -> {
            ContractStatus targetStatus = inv.getArgument(0);
            return contractStore.values().stream()
                    .filter(c -> !c.isDeleted() && c.getStatus() == targetStatus)
                    .toList();
        });
        lenient().when(interestPaymentRepository.save(any(InterestPaymentEntity.class))).thenAnswer(inv -> {
            InterestPaymentEntity p = inv.getArgument(0);
            if (p.getId() == null) {
                p.setId(paymentIdSeq.getAndIncrement());
            }
            paymentStore.removeIf(existing -> existing.getId().equals(p.getId()));
            paymentStore.add(p);
            return p;
        });

        lenient().when(scheduleRepository.saveAll(any())).thenAnswer(inv -> {
            Iterable<ContractPaymentScheduleEntity> items = inv.getArgument(0);
            List<ContractPaymentScheduleEntity> saved = new ArrayList<>();
            for (ContractPaymentScheduleEntity s : items) {
                if (s.getId() == null) {
                    s.setId(scheduleIdSeq.getAndIncrement());
                }
                scheduleStore.put(s.getId(), s);
                saved.add(s);
            }
            return saved;
        });
        lenient().when(scheduleRepository.findByContractIdAndDeletedFalseOrderByPeriodNumberAsc(anyLong())).thenAnswer(inv -> {
            Long cId = inv.getArgument(0);
            return scheduleStore.values().stream()
                    .filter(s -> !s.isDeleted() && cId.equals(s.getContractId()))
                    .sorted(Comparator.comparing(ContractPaymentScheduleEntity::getPeriodNumber))
                    .toList();
        });
        lenient().when(scheduleRepository.findByContractIdInAndDeletedFalseOrderByContractIdAscPeriodNumberAsc(anyCollection()))
                .thenAnswer(inv -> {
                    Collection<Long> ids = inv.getArgument(0);
                    return scheduleStore.values().stream()
                            .filter(s -> !s.isDeleted() && ids.contains(s.getContractId()))
                            .sorted(Comparator.comparing(ContractPaymentScheduleEntity::getContractId)
                                    .thenComparing(ContractPaymentScheduleEntity::getPeriodNumber))
                            .toList();
                });
        lenient().when(scheduleRepository.findOpenSchedulesDueBefore(any(LocalDate.class))).thenAnswer(inv -> {
            LocalDate cutoff = inv.getArgument(0);
            return scheduleStore.values().stream()
                    .filter(s -> !s.isDeleted()
                            && s.getStatus() == ScheduleStatus.SCHEDULED
                            && s.getDueDate().isBefore(cutoff)
                            && s.getContract() != null
                            && !s.getContract().isDeleted()
                            && (s.getContract().getStatus() == ContractStatus.ACTIVE
                                || s.getContract().getStatus() == ContractStatus.EXTENDED
                                || s.getContract().getStatus() == ContractStatus.OVERDUE))
                    .toList();
        });
    }

    private void setFixedDate(LocalDate date) {
        Instant instant = date.atTime(10, 0).atZone(ZONE).toInstant();
        contractService.setClock(Clock.fixed(instant, ZONE));
    }

    private ContractResponse createSampleContract(LocalDate date, int loanTermDays, int cycleDays, BigDecimal principal) {
        collateral.setStatus(CollateralStatus.IN_VAULT);
        setFixedDate(date);
        ContractCreateRequest req = ContractCreateRequest.builder()
                .customerId(100L)
                .collateralId(200L)
                .branchId(1L)
                .principalAmount(principal)
                .interestType(InterestType.DAILY_PER_MILLION)
                .interestRate(new BigDecimal("2000"))
                .storageFeeRate(new BigDecimal("500"))
                .loanTermDays(loanTermDays)
                .paymentCycleDays(cycleDays)
                .build();
        return contractService.createContract(req, 1L);
    }

    @Test
    @DisplayName("Q1a: Creation generates PROJECTED schedule rows with exact non-divisible final period slicing")
    void createContract_GeneratesProjectedScheduleWithNonDivisibleFinalPeriod() {
        ContractResponse created = createSampleContract(
                LocalDate.of(2026, 11, 10), 25, 10, new BigDecimal("30000000.00"));

        assertEquals(ContractStatus.PENDING_APPROVAL, created.getStatus());
        assertEquals(3, created.getTotalPeriodsCount());
        assertEquals(0, created.getPaidPeriodsCount());
        assertEquals(1, created.getCurrentPeriodNumber());

        List<ContractPaymentScheduleResponse> rows = created.getPaymentSchedules();
        assertEquals(3, rows.size());

        // Period 1: 10 days (11-10 -> 11-20), 600k interest + 150k storage = 750k
        assertEquals(ScheduleStatus.PROJECTED, rows.get(0).getStatus());
        assertEquals(10, rows.get(0).getDaysCount());
        assertEquals(LocalDate.of(2026, 11, 20), rows.get(0).getDueDate());
        assertEquals(0, new BigDecimal("750000.00").compareTo(rows.get(0).getExpectedTotalAmount()));
        assertFalse(rows.get(0).getFinalPeriod());

        // Period 3: 5 remaining days (11-30 -> 12-05), 300k + 75k = 375k, finalPeriod = true
        ContractPaymentScheduleResponse p3 = rows.get(2);
        assertEquals(3, p3.getPeriodNumber());
        assertEquals(5, p3.getDaysCount());
        assertEquals(LocalDate.of(2026, 11, 30), p3.getFromDate());
        assertEquals(LocalDate.of(2026, 12, 5), p3.getDueDate());
        assertEquals(0, new BigDecimal("375000.00").compareTo(p3.getExpectedTotalAmount()));
        assertTrue(p3.getFinalPeriod());
    }

    @Test
    @DisplayName("Q1b: Delayed approval shifts all projected periods to approvalDate and rejection zeroes & cancels them")
    void delayedApprovalShiftsScheduleDates_AndRejectionCancels() {
        ContractResponse created = createSampleContract(
                LocalDate.of(2026, 11, 10), 25, 10, new BigDecimal("30000000.00"));

        // Approve 3 days later on 2026-11-13
        LocalDate approvalDate = LocalDate.of(2026, 11, 13);
        setFixedDate(approvalDate);
        ContractResponse approved = contractService.approveAndDisburseContract(created.getId(), 2L, "Duyệt giải ngân");

        assertEquals(ContractStatus.ACTIVE, approved.getStatus());
        assertEquals(LocalDate.of(2026, 11, 13), approved.getStartDate());
        assertEquals(LocalDate.of(2026, 11, 23), approved.getNextInterestDueDate());
        assertEquals(LocalDate.of(2026, 12, 8), approved.getMaturityDate());

        List<ContractPaymentScheduleResponse> rows = approved.getPaymentSchedules();
        assertEquals(ScheduleStatus.SCHEDULED, rows.get(0).getStatus());
        assertEquals(LocalDate.of(2026, 11, 13), rows.get(0).getFromDate());
        assertEquals(LocalDate.of(2026, 11, 23), rows.get(0).getDueDate());
        assertEquals(LocalDate.of(2026, 12, 8), rows.get(2).getDueDate());
        assertNotNull(rows.get(0).getAdjustmentNote());

        // Separate rejection contract check
        ContractResponse secondCreated = createSampleContract(
                LocalDate.of(2026, 11, 14), 30, 10, new BigDecimal("20000000.00"));
        ContractResponse rejected = contractService.rejectContract(secondCreated.getId(), 2L, "Hồ sơ thiếu ảnh niêm phong");

        assertEquals(ContractStatus.CANCELLED, rejected.getStatus());
        for (ContractPaymentScheduleResponse row : rejected.getPaymentSchedules()) {
            assertEquals(ScheduleStatus.CANCELLED, row.getStatus());
            assertEquals(0, BigDecimal.ZERO.compareTo(row.getExpectedTotalAmount()));
            assertFalse(row.getFinalPeriod());
        }
    }

    @Test
    @DisplayName("Q4: Early periodic collection preserves fixed cycle anchors; paying final period rolls into EXTENDED cycle N+1")
    void periodicInterest_FixedAnchorsAndFinalCycleExtension() {
        ContractResponse created = createSampleContract(
                LocalDate.of(2026, 11, 1), 25, 10, new BigDecimal("30000000.00"));
        setFixedDate(LocalDate.of(2026, 11, 1));
        ContractResponse approved = contractService.approveAndDisburseContract(created.getId(), 2L, "Giải ngân");
        Long contractId = approved.getId();

        // Pay Period 1 3 days EARLY on 2026-11-08 (due was 2026-11-11)
        setFixedDate(LocalDate.of(2026, 11, 8));
        contractService.collectInterest(contractId, InterestCollectionRequest.builder()
                .interestAmount(new BigDecimal("600000.00"))
                .storageFeeAmount(new BigDecimal("150000.00"))
                .build(), 1L);

        ContractEntity afterP1 = contractStore.get(contractId);
        // Next due date stays pinned to Period 2 anchor (2026-11-21), NOT 2026-11-18
        assertEquals(LocalDate.of(2026, 11, 21), afterP1.getNextInterestDueDate());
        assertEquals(ContractStatus.ACTIVE, afterP1.getStatus());

        // Pay Period 2
        setFixedDate(LocalDate.of(2026, 11, 21));
        contractService.collectInterest(contractId, InterestCollectionRequest.builder()
                .interestAmount(new BigDecimal("600000.00"))
                .storageFeeAmount(new BigDecimal("150000.00"))
                .build(), 1L);

        assertEquals(LocalDate.of(2026, 11, 26), contractStore.get(contractId).getNextInterestDueDate());

        // Pay final Period 3 (5 days = 375k) while principal 30M still > 0 -> rolls Period 4 [11-26..12-06]
        setFixedDate(LocalDate.of(2026, 11, 26));
        contractService.collectInterest(contractId, InterestCollectionRequest.builder()
                .interestAmount(new BigDecimal("300000.00"))
                .storageFeeAmount(new BigDecimal("75000.00"))
                .build(), 1L);

        ContractEntity extended = contractStore.get(contractId);
        assertEquals(ContractStatus.EXTENDED, extended.getStatus());
        assertEquals(LocalDate.of(2026, 12, 6), extended.getMaturityDate());
        assertEquals(LocalDate.of(2026, 12, 6), extended.getNextInterestDueDate());

        List<ContractPaymentScheduleResponse> schedules = contractService.getContractSchedules(contractId);
        assertEquals(4, schedules.size());
        assertEquals(ScheduleStatus.PAID, schedules.get(2).getStatus());
        assertFalse(schedules.get(2).getFinalPeriod());
        assertEquals(ScheduleStatus.SCHEDULED, schedules.get(3).getStatus());
        assertTrue(schedules.get(3).getFinalPeriod());
        assertEquals(LocalDate.of(2026, 11, 26), schedules.get(3).getFromDate());
        assertEquals(LocalDate.of(2026, 12, 6), schedules.get(3).getDueDate());
        assertEquals(0, new BigDecimal("750000.00").compareTo(schedules.get(3).getExpectedTotalAmount()));
    }

    @Test
    @DisplayName("Q2: Mid-cycle PARTIAL_PRINCIPAL settles elapsed days on old principal and regenerates remaining horizon on reduced principal")
    void partialPrincipal_MidCycleAndFullPaydownRecalculation() {
        ContractResponse created = createSampleContract(
                LocalDate.of(2026, 11, 1), 30, 10, new BigDecimal("30000000.00"));
        setFixedDate(LocalDate.of(2026, 11, 1));
        contractService.approveAndDisburseContract(created.getId(), 2L, "Giải ngân");
        Long contractId = created.getId();

        // Day 4 (2026-11-05): customer pays 4-day interest (240k + 60k) + 10M principal paydown
        setFixedDate(LocalDate.of(2026, 11, 5));
        contractService.collectInterest(contractId, InterestCollectionRequest.builder()
                .interestAmount(new BigDecimal("240000.00"))
                .storageFeeAmount(new BigDecimal("60000.00"))
                .principalPaidAmount(new BigDecimal("10000000.00"))
                .build(), 1L);

        ContractEntity updated = contractStore.get(contractId);
        assertEquals(0, new BigDecimal("20000000.00").compareTo(updated.getRemainingPrincipal()));
        assertEquals(LocalDate.of(2026, 11, 15), updated.getNextInterestDueDate());
        assertEquals(LocalDate.of(2026, 12, 1), updated.getMaturityDate());

        // Horizon 11-05 -> 12-01 (26 days): P2 10d, P3 10d, P4 6d on 20M principal
        List<ContractPaymentScheduleResponse> rows = contractService.getContractSchedules(contractId);
        assertEquals(4, rows.size());

        ContractPaymentScheduleResponse p1 = rows.get(0);
        assertEquals(ScheduleStatus.PAID, p1.getStatus());
        assertEquals(4, p1.getDaysCount());
        assertEquals(0, new BigDecimal("30000000.00").compareTo(p1.getPrincipalBase()));
        assertEquals(0, new BigDecimal("300000.00").compareTo(p1.getPaidTotalAmount()));

        ContractPaymentScheduleResponse p2 = rows.get(1);
        assertEquals(ScheduleStatus.SCHEDULED, p2.getStatus());
        assertEquals(LocalDate.of(2026, 11, 5), p2.getFromDate());
        assertEquals(LocalDate.of(2026, 11, 15), p2.getDueDate());
        assertEquals(0, new BigDecimal("20000000.00").compareTo(p2.getPrincipalBase()));
        assertEquals(0, new BigDecimal("500000.00").compareTo(p2.getExpectedTotalAmount()));

        ContractPaymentScheduleResponse p4 = rows.get(3);
        assertEquals(6, p4.getDaysCount());
        assertTrue(p4.getFinalPeriod());
        assertEquals(0, new BigDecimal("300000.00").compareTo(p4.getExpectedTotalAmount()));

        // Pay down remaining 20M to 0 on 2026-11-15 -> cancels remaining cycles while keeping P1 untouched
        setFixedDate(LocalDate.of(2026, 11, 15));
        contractService.collectInterest(contractId, InterestCollectionRequest.builder()
                .interestAmount(new BigDecimal("400000.00"))
                .storageFeeAmount(new BigDecimal("100000.00"))
                .principalPaidAmount(new BigDecimal("20000000.00"))
                .build(), 1L);

        List<ContractPaymentScheduleResponse> afterZero = contractService.getContractSchedules(contractId);
        assertEquals(ScheduleStatus.PAID, afterZero.get(0).getStatus());
        assertEquals(0, new BigDecimal("30000000.00").compareTo(afterZero.get(0).getPrincipalBase()));
        assertEquals(ScheduleStatus.PAID, afterZero.get(1).getStatus());
        assertEquals(ScheduleStatus.CANCELLED, afterZero.get(2).getStatus());
        assertEquals(0, BigDecimal.ZERO.compareTo(afterZero.get(2).getExpectedTotalAmount()));
        assertEquals(ScheduleStatus.CANCELLED, afterZero.get(3).getStatus());
        assertEquals(0, BigDecimal.ZERO.compareTo(afterZero.get(3).getExpectedTotalAmount()));
    }

    @Test
    @DisplayName("Q3a: Early REDEMPTION computes pro-rata days, settles active period SETTLED_EARLY, cancels future periods, and records dual cashbook inflows")
    void redeemContract_MidCycleProRataAndCancelsFuturePeriods() {
        ContractResponse created = createSampleContract(
                LocalDate.of(2026, 11, 1), 30, 10, new BigDecimal("30000000.00"));
        setFixedDate(LocalDate.of(2026, 11, 1));
        contractService.approveAndDisburseContract(created.getId(), 2L, "Giải ngân");
        Long contractId = created.getId();

        // Pay Period 1 [11-01..11-11] = 750k
        setFixedDate(LocalDate.of(2026, 11, 11));
        contractService.collectInterest(contractId, InterestCollectionRequest.builder()
                .interestAmount(new BigDecimal("600000.00"))
                .storageFeeAmount(new BigDecimal("150000.00"))
                .build(), 1L);

        // Redeem on 2026-11-15 -> 4 elapsed days on 30M => 240k interest + 60k storage = 300k
        setFixedDate(LocalDate.of(2026, 11, 15));
        ContractSettlementPreviewResponse preview = contractService.getContractSettlementPreview(contractId, LocalDate.of(2026, 11, 15));
        assertEquals(4, preview.getRedemptionElapsedDays());
        assertEquals(0, new BigDecimal("240000.00").compareTo(preview.getRedemptionInterestAmount()));
        assertEquals(0, new BigDecimal("60000.00").compareTo(preview.getRedemptionStorageFeeAmount()));
        assertEquals(0, new BigDecimal("30300000.00").compareTo(preview.getRedemptionTotalSettlement()));
        assertEquals(1, preview.getRedemptionCancelledPeriodsCount());

        ContractResponse redeemed = contractService.redeemContract(contractId, 1L, "Khách chuộc sớm ngày 15/11");
        assertEquals(ContractStatus.REDEEMED, redeemed.getStatus());
        assertEquals(0, BigDecimal.ZERO.compareTo(redeemed.getRemainingPrincipal()));
        // totalInterestPaid = 750k (P1) + 300k (pro-rata redemption) = 1,050,000
        assertEquals(0, new BigDecimal("1050000.00").compareTo(redeemed.getTotalInterestPaid()));

        List<ContractPaymentScheduleResponse> rows = redeemed.getPaymentSchedules();
        assertEquals(ScheduleStatus.PAID, rows.get(0).getStatus());
        assertEquals(ScheduleStatus.SETTLED_EARLY, rows.get(1).getStatus());
        assertEquals(4, rows.get(1).getDaysCount());
        assertEquals(LocalDate.of(2026, 11, 15), rows.get(1).getToDate());
        assertEquals(0, new BigDecimal("300000.00").compareTo(rows.get(1).getPaidTotalAmount()));
        assertEquals(ScheduleStatus.CANCELLED, rows.get(2).getStatus());
        assertEquals(0, BigDecimal.ZERO.compareTo(rows.get(2).getExpectedTotalAmount()));

        // Verify REDEMPTION payment voucher created and linked
        InterestPaymentEntity redemptionVoucher = paymentStore.stream()
                .filter(p -> p.getPaymentType() == PaymentType.REDEMPTION)
                .findFirst()
                .orElseThrow();
        assertEquals(rows.get(1).getId(), redemptionVoucher.getScheduleId());

        verify(cashbookService).collectCashIn(
                eq(1L),
                eq(new BigDecimal("30000000.00")),
                eq(TransactionCategory.PRINCIPAL_COLLECTION),
                eq(contractId),
                eq(1L),
                anyString());
        verify(cashbookService).collectCashIn(
                eq(1L),
                eq(new BigDecimal("300000.00")),
                eq(TransactionCategory.INTEREST_COLLECTION),
                eq(contractId),
                eq(1L),
                anyString());
    }

    @Test
    @DisplayName("Q3b & Q3c: Same-day redemption charges minimum 1 day; prepaid redemption waives extra interest and keeps PAID rows intact")
    void redeemContract_Day0MinimumOneDay_AndPrepaidZeroExtraDays() {
        // Case 1: Day-0 minimum 1 day
        ContractResponse c1 = createSampleContract(
                LocalDate.of(2026, 11, 1), 30, 10, new BigDecimal("30000000.00"));
        setFixedDate(LocalDate.of(2026, 11, 1));
        contractService.approveAndDisburseContract(c1.getId(), 2L, "Giải ngân");

        ContractSettlementPreviewResponse day0Preview = contractService.getContractSettlementPreview(
                c1.getId(), LocalDate.of(2026, 11, 1));
        assertEquals(1, day0Preview.getRedemptionElapsedDays());
        assertEquals(0, new BigDecimal("60000.00").compareTo(day0Preview.getRedemptionInterestAmount()));
        assertEquals(0, new BigDecimal("15000.00").compareTo(day0Preview.getRedemptionStorageFeeAmount()));

        // Case 2: Prepaid past redemption date -> 0 extra days, 0 INTEREST_COLLECTION
        ContractResponse c2 = createSampleContract(
                LocalDate.of(2026, 11, 1), 30, 10, new BigDecimal("30000000.00"));
        setFixedDate(LocalDate.of(2026, 11, 1));
        contractService.approveAndDisburseContract(c2.getId(), 2L, "Giải ngân");
        setFixedDate(LocalDate.of(2026, 11, 2));
        contractService.collectInterest(c2.getId(), InterestCollectionRequest.builder()
                .interestAmount(new BigDecimal("600000.00"))
                .storageFeeAmount(new BigDecimal("150000.00"))
                .build(), 1L);

        setFixedDate(LocalDate.of(2026, 11, 5));
        ContractSettlementPreviewResponse prepaidPreview = contractService.getContractSettlementPreview(
                c2.getId(), LocalDate.of(2026, 11, 5));
        assertEquals(0, prepaidPreview.getRedemptionElapsedDays());
        assertEquals(0, BigDecimal.ZERO.compareTo(prepaidPreview.getRedemptionTotalInterestAndFees()));

        ContractResponse prepaidRedeemed = contractService.redeemContract(c2.getId(), ContractRedeemRequest.builder()
                .notes("Chuộc sớm khi đã đóng trước kỳ 1")
                .build(), 1L);
        List<ContractPaymentScheduleResponse> c2Rows = prepaidRedeemed.getPaymentSchedules();
        assertEquals(ScheduleStatus.PAID, c2Rows.get(0).getStatus());
        assertEquals(ScheduleStatus.CANCELLED, c2Rows.get(1).getStatus());
        assertEquals(ScheduleStatus.CANCELLED, c2Rows.get(2).getStatus());
        verify(cashbookService).collectCashIn(
                eq(1L),
                eq(new BigDecimal("30000000.00")),
                eq(TransactionCategory.PRINCIPAL_COLLECTION),
                eq(c2.getId()),
                eq(1L),
                anyString());
        verify(cashbookService, never()).collectCashIn(
                anyLong(),
                any(BigDecimal.class),
                eq(TransactionCategory.INTEREST_COLLECTION),
                eq(c2.getId()),
                anyLong(),
                eq("Thu lãi & phí tất toán hợp đồng " + c2.getContractCode()));
    }

    @Test
    @DisplayName("Q5: markOverdueContracts marks past-due SCHEDULED rows OVERDUE and liquidateContract zeroes & cancels open rows")
    void overdueScanAndLiquidation_UpdateScheduleStatusesAccurately() {
        ContractResponse created = createSampleContract(
                LocalDate.of(2026, 11, 1), 30, 10, new BigDecimal("20000000.00"));
        setFixedDate(LocalDate.of(2026, 11, 1));
        contractService.approveAndDisburseContract(created.getId(), 2L, "Giải ngân");
        Long contractId = created.getId();

        // Scan overdue as of 2026-12-05 (past maturity 2026-12-01 and all 3 due dates)
        int markedContracts = contractService.markOverdueContracts(LocalDate.of(2026, 12, 5));
        assertEquals(1, markedContracts);

        List<ContractPaymentScheduleResponse> afterOverdue = contractService.getContractSchedules(contractId);
        assertTrue(afterOverdue.stream().allMatch(r -> r.getStatus() == ScheduleStatus.OVERDUE));

        // Liquidate contract -> remaining open cycles become CANCELLED with 0 expected amount
        setFixedDate(LocalDate.of(2026, 12, 10));
        ContractResponse liquidated = contractService.liquidateContract(contractId, LiquidationRequest.builder()
                .liquidationAmount(new BigDecimal("25000000.00"))
                .notes("Thanh lý đấu giá tài sản quá hạn")
                .build(), 2L);

        assertEquals(ContractStatus.LIQUIDATED, liquidated.getStatus());
        for (ContractPaymentScheduleResponse row : liquidated.getPaymentSchedules()) {
            assertEquals(ScheduleStatus.CANCELLED, row.getStatus());
            assertEquals(0, BigDecimal.ZERO.compareTo(row.getExpectedTotalAmount()));
        }
    }

    @Test
    @DisplayName("Math parity: computeCycleBreakdown rounds whole VND accurately for PERCENT_MONTHLY and PERCENT_WEEKLY")
    void cycleBreakdownMathParity() {
        ContractScheduleServiceImpl.CycleBreakdown monthly = ContractScheduleServiceImpl.computeCycleBreakdown(
                new BigDecimal("20000000.00"),
                InterestType.PERCENT_MONTHLY,
                new BigDecimal("3.0"),
                new BigDecimal("0.5"),
                15
        );
        assertEquals(0, new BigDecimal("300000.00").compareTo(monthly.interest()));
        assertEquals(0, new BigDecimal("50000.00").compareTo(monthly.storageFee()));
        assertEquals(0, new BigDecimal("350000.00").compareTo(monthly.total()));

        ContractScheduleServiceImpl.CycleBreakdown weekly = ContractScheduleServiceImpl.computeCycleBreakdown(
                new BigDecimal("14000000.00"),
                InterestType.PERCENT_WEEKLY,
                new BigDecimal("1.0"),
                new BigDecimal("0.5"),
                14
        );
        assertEquals(0, new BigDecimal("280000.00").compareTo(weekly.interest()));
        assertEquals(0, new BigDecimal("140000.00").compareTo(weekly.storageFee()));
        assertEquals(0, new BigDecimal("420000.00").compareTo(weekly.total()));
    }
}
