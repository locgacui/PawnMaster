package com.pawnmaster.api.modules.contract;

import com.pawnmaster.api.modules.contract.dto.ContractPaymentScheduleResponse;
import com.pawnmaster.api.modules.contract.dto.ContractResponse;
import com.pawnmaster.api.modules.contract.dto.ContractSettlementPreviewResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ContractScheduleServiceImpl implements ContractScheduleService {

    private static final BigDecimal MILLION = new BigDecimal("1000000");
    private static final BigDecimal HUNDRED = new BigDecimal("100");
    private static final BigDecimal THIRTY = new BigDecimal("30");
    private static final BigDecimal SEVEN = new BigDecimal("7");
    private static final int DEFAULT_CYCLE_DAYS = 10;
    private static final int DEFAULT_LOAN_TERM_DAYS = 30;

    private final ContractPaymentScheduleRepository scheduleRepository;

    private Clock clock = Clock.systemDefaultZone();

    @Override
    public void setClock(Clock clock) {
        this.clock = clock != null ? clock : Clock.systemDefaultZone();
    }

    @Override
    @Transactional
    public List<ContractPaymentScheduleResponse> generateInitialSchedule(ContractEntity contract) {
        if (contract == null || contract.getId() == null) {
            return List.of();
        }
        LocalDate start = contract.getStartDate() != null ? contract.getStartDate() : LocalDate.now(clock);
        int loanTerm = positiveOrDefault(contract.getLoanTermDays(), DEFAULT_LOAN_TERM_DAYS);
        LocalDate maturity = contract.getMaturityDate() != null ? contract.getMaturityDate() : start.plusDays(loanTerm);
        int cycleDays = positiveOrDefault(contract.getPaymentCycleDays(), DEFAULT_CYCLE_DAYS);
        BigDecimal principal = resolveEffectivePrincipal(contract);
        ScheduleStatus initialStatus = contract.getStatus() == ContractStatus.PENDING_APPROVAL
                ? ScheduleStatus.PROJECTED
                : ScheduleStatus.SCHEDULED;

        List<ContractPaymentScheduleEntity> existing = scheduleRepository
                .findByContractIdAndDeletedFalseOrderByPeriodNumberAsc(contract.getId());
        Map<Integer, ContractPaymentScheduleEntity> existingByPeriod = indexByPeriod(existing);

        List<DateSlice> slices = buildDateSlices(start, maturity, cycleDays, 1);
        List<ContractPaymentScheduleEntity> toSave = new ArrayList<>();
        for (DateSlice slice : slices) {
            CycleBreakdown breakdown = computeCycleBreakdown(
                    principal,
                    contract.getInterestType(),
                    contract.getInterestRate(),
                    contract.getStorageFeeRate(),
                    slice.daysCount()
            );
            ContractPaymentScheduleEntity row = upsertRow(existingByPeriod, contract, slice.periodNumber());
            applyPlannedSlice(row, slice, principal, breakdown, initialStatus, null);
            toSave.add(row);
        }

        cancelExtraExistingRows(existing, slices.size(), toSave,
                "Hủy kỳ dư khi khởi tạo lại lịch thu lãi", true);

        List<ContractPaymentScheduleEntity> saved = scheduleRepository.saveAll(toSave);
        saved.sort(Comparator.comparing(ContractPaymentScheduleEntity::getPeriodNumber));
        return saved.stream().map(ContractPaymentScheduleResponse::fromEntity).toList();
    }

    @Override
    @Transactional
    public List<ContractPaymentScheduleResponse> activateScheduleOnApproval(ContractEntity contract, LocalDate approvalDate) {
        if (contract == null || contract.getId() == null) {
            return List.of();
        }
        LocalDate effectiveStart = approvalDate != null ? approvalDate : LocalDate.now(clock);
        int loanTerm = positiveOrDefault(contract.getLoanTermDays(), DEFAULT_LOAN_TERM_DAYS);
        LocalDate maturity = effectiveStart.plusDays(loanTerm);
        int cycleDays = positiveOrDefault(contract.getPaymentCycleDays(), DEFAULT_CYCLE_DAYS);
        BigDecimal principal = resolveEffectivePrincipal(contract);

        List<ContractPaymentScheduleEntity> existing = scheduleRepository
                .findByContractIdAndDeletedFalseOrderByPeriodNumberAsc(contract.getId());
        LocalDate previousStart = existing.isEmpty() ? null : existing.get(0).getFromDate();
        boolean dateShifted = previousStart != null && !previousStart.equals(effectiveStart);
        String note = dateShifted
                ? "Dịch lịch chính thức theo ngày duyệt giải ngân thực tế (" + effectiveStart + ")"
                : null;

        Map<Integer, ContractPaymentScheduleEntity> existingByPeriod = indexByPeriod(existing);
        List<DateSlice> slices = buildDateSlices(effectiveStart, maturity, cycleDays, 1);
        List<ContractPaymentScheduleEntity> toSave = new ArrayList<>();
        for (DateSlice slice : slices) {
            CycleBreakdown breakdown = computeCycleBreakdown(
                    principal,
                    contract.getInterestType(),
                    contract.getInterestRate(),
                    contract.getStorageFeeRate(),
                    slice.daysCount()
            );
            ContractPaymentScheduleEntity row = upsertRow(existingByPeriod, contract, slice.periodNumber());
            applyPlannedSlice(row, slice, principal, breakdown, ScheduleStatus.SCHEDULED, note);
            toSave.add(row);
        }

        cancelExtraExistingRows(existing, slices.size(), toSave,
                "Hủy kỳ dư khi kích hoạt lịch chính thức lúc giải ngân", true);

        List<ContractPaymentScheduleEntity> saved = scheduleRepository.saveAll(toSave);
        saved.sort(Comparator.comparing(ContractPaymentScheduleEntity::getPeriodNumber));
        if (!saved.isEmpty()) {
            contract.setNextInterestDueDate(saved.get(0).getDueDate());
        }
        return saved.stream().map(ContractPaymentScheduleResponse::fromEntity).toList();
    }

    @Override
    @Transactional
    public void cancelScheduleOnRejection(ContractEntity contract, String reason) {
        if (contract == null || contract.getId() == null) {
            return;
        }
        List<ContractPaymentScheduleEntity> existing = scheduleRepository
                .findByContractIdAndDeletedFalseOrderByPeriodNumberAsc(contract.getId());
        if (existing.isEmpty()) {
            return;
        }
        String note = "Hủy lịch dự kiến do hợp đồng bị từ chối/hủy"
                + (reason != null && !reason.isBlank() ? ": " + reason.trim() : "");
        List<ContractPaymentScheduleEntity> modified = new ArrayList<>();
        for (ContractPaymentScheduleEntity row : existing) {
            if (!isSettledStatus(row.getStatus())) {
                zeroAndCancelRow(row, note);
                modified.add(row);
            }
        }
        if (!modified.isEmpty()) {
            scheduleRepository.saveAll(modified);
        }
    }

    @Override
    @Transactional
    public void applyCollection(
            ContractEntity contract,
            InterestPaymentEntity savedPayment,
            PaymentType paymentType,
            BigDecimal oldRemainingPrincipal,
            BigDecimal newRemainingPrincipal,
            LocalDate paymentDate
    ) {
        if (contract == null || contract.getId() == null || savedPayment == null) {
            return;
        }
        LocalDate effectiveDate = paymentDate != null ? paymentDate : LocalDate.now(clock);
        List<ContractPaymentScheduleEntity> rows = new ArrayList<>(scheduleRepository
                .findByContractIdAndDeletedFalseOrderByPeriodNumberAsc(contract.getId()));
        if (rows.isEmpty()) {
            generateInitialSchedule(contract);
            rows = new ArrayList<>(scheduleRepository
                    .findByContractIdAndDeletedFalseOrderByPeriodNumberAsc(contract.getId()));
        }

        BigDecimal oldPrincipal = nvl(oldRemainingPrincipal);
        BigDecimal newPrincipal = nvl(newRemainingPrincipal);
        if (paymentType == PaymentType.PARTIAL_PRINCIPAL) {
            handlePartialPrincipalCollection(contract, savedPayment, rows, oldPrincipal, newPrincipal, effectiveDate);
        } else {
            handlePeriodicInterestCollection(contract, savedPayment, rows, newPrincipal, effectiveDate);
        }
    }

    @Override
    @Transactional
    public ContractSettlementPreviewResponse settleScheduleOnRedemption(
            ContractEntity contract,
            InterestPaymentEntity savedRedemptionPayment,
            BigDecimal preRedemptionPrincipal,
            LocalDate redemptionDate
    ) {
        if (contract == null || contract.getId() == null) {
            return null;
        }
        LocalDate effectiveDate = redemptionDate != null ? redemptionDate : LocalDate.now(clock);
        BigDecimal principalBeforeRedeem = nvl(preRedemptionPrincipal);
        ContractSettlementPreviewResponse preview = buildPreviewWithPrincipal(
                contract, principalBeforeRedeem, effectiveDate);

        List<ContractPaymentScheduleEntity> rows = scheduleRepository
                .findByContractIdAndDeletedFalseOrderByPeriodNumberAsc(contract.getId());
        if (rows.isEmpty()) {
            return preview;
        }

        BigDecimal paidInt = savedRedemptionPayment != null ? nvl(savedRedemptionPayment.getInterestAmount()) : BigDecimal.ZERO;
        BigDecimal paidSto = savedRedemptionPayment != null ? nvl(savedRedemptionPayment.getStorageFeeAmount()) : BigDecimal.ZERO;
        BigDecimal paidPen = savedRedemptionPayment != null ? nvl(savedRedemptionPayment.getPenaltyAmount()) : BigDecimal.ZERO;
        BigDecimal paidTotal = paidInt.add(paidSto).add(paidPen);
        LocalDateTime settledAt = savedRedemptionPayment != null && savedRedemptionPayment.getPaymentDate() != null
                ? savedRedemptionPayment.getPaymentDate()
                : LocalDateTime.now(clock);

        List<ContractPaymentScheduleEntity> openRows = rows.stream()
                .filter(r -> isOpenStatus(r.getStatus()))
                .toList();
        LocalDate paidThrough = findPaidThroughDate(rows, contract.getStartDate());
        int elapsedDays = preview.getRedemptionElapsedDays() != null ? preview.getRedemptionElapsedDays() : 0;

        List<ContractPaymentScheduleEntity> toSave = new ArrayList<>();
        if (elapsedDays > 0 || paidTotal.compareTo(BigDecimal.ZERO) > 0) {
            ContractPaymentScheduleEntity settledRow;
            if (!openRows.isEmpty()) {
                settledRow = openRows.get(0);
            } else {
                int nextPeriod = rows.stream()
                        .mapToInt(ContractPaymentScheduleEntity::getPeriodNumber)
                        .max()
                        .orElse(0) + 1;
                settledRow = ContractPaymentScheduleEntity.builder()
                        .contract(contract)
                        .periodNumber(nextPeriod)
                        .build();
            }

            LocalDate settledFrom = paidThrough != null ? paidThrough : effectiveDate;
            LocalDate settledTo = effectiveDate.isBefore(settledFrom) ? settledFrom : effectiveDate;
            CycleBreakdown expectedProRata = computeCycleBreakdown(
                    principalBeforeRedeem,
                    contract.getInterestType(),
                    contract.getInterestRate(),
                    contract.getStorageFeeRate(),
                    elapsedDays
            );

            settledRow.setFromDate(settledFrom);
            settledRow.setToDate(settledTo);
            settledRow.setDueDate(settledTo);
            settledRow.setDaysCount(elapsedDays);
            settledRow.setPrincipalBase(principalBeforeRedeem);
            settledRow.setExpectedInterestAmount(expectedProRata.interest());
            settledRow.setExpectedStorageFeeAmount(expectedProRata.storageFee());
            settledRow.setExpectedTotalAmount(expectedProRata.total());
            settledRow.setPaidInterestAmount(paidInt);
            settledRow.setPaidStorageFeeAmount(paidSto);
            settledRow.setPaidPenaltyAmount(paidPen);
            settledRow.setPaidTotalAmount(paidTotal);
            settledRow.setStatus(ScheduleStatus.SETTLED_EARLY);
            settledRow.setFinalPeriod(Boolean.FALSE);
            settledRow.setSettledAt(settledAt);
            settledRow.setInterestPayment(savedRedemptionPayment);
            settledRow.setAdjustmentNote("Tất toán chuộc tài sản sớm ngày " + effectiveDate
                    + " (tính " + elapsedDays + " ngày thực tế từ " + settledFrom + ")");
            toSave.add(settledRow);

            if (savedRedemptionPayment != null) {
                savedRedemptionPayment.setSchedule(settledRow);
            }

            for (int i = 1; i < openRows.size(); i++) {
                ContractPaymentScheduleEntity futureRow = openRows.get(i);
                zeroAndCancelRow(futureRow,
                        "Hủy kỳ chưa tới hạn do khách tất toán chuộc tài sản sớm ngày " + effectiveDate);
                toSave.add(futureRow);
            }
        } else {
            for (ContractPaymentScheduleEntity futureRow : openRows) {
                zeroAndCancelRow(futureRow,
                        "Hủy kỳ chưa tới hạn do hợp đồng đã đóng lãi vượt ngày tất toán " + effectiveDate);
                toSave.add(futureRow);
            }
        }

        if (!toSave.isEmpty()) {
            scheduleRepository.saveAll(toSave);
        }
        return previewSettlement(contract, effectiveDate);
    }

    @Override
    @Transactional
    public void closeScheduleOnLiquidation(ContractEntity contract, LocalDate liquidationDate, String reason) {
        if (contract == null || contract.getId() == null) {
            return;
        }
        LocalDate effectiveDate = liquidationDate != null ? liquidationDate : LocalDate.now(clock);
        List<ContractPaymentScheduleEntity> rows = scheduleRepository
                .findByContractIdAndDeletedFalseOrderByPeriodNumberAsc(contract.getId());
        String note = "Hủy kỳ chưa thanh toán do thanh lý tài sản ngày " + effectiveDate
                + (reason != null && !reason.isBlank() ? " (" + reason.trim() + ")" : "");
        List<ContractPaymentScheduleEntity> toSave = new ArrayList<>();
        for (ContractPaymentScheduleEntity row : rows) {
            if (isOpenStatus(row.getStatus())) {
                zeroAndCancelRow(row, note);
                toSave.add(row);
            }
        }
        if (!toSave.isEmpty()) {
            scheduleRepository.saveAll(toSave);
        }
    }

    @Override
    @Transactional
    public int markOverdueSchedules(LocalDate asOfDate) {
        LocalDate effectiveDate = asOfDate != null ? asOfDate : LocalDate.now(clock);
        List<ContractPaymentScheduleEntity> overdueRows = scheduleRepository.findOpenSchedulesDueBefore(effectiveDate);
        if (overdueRows.isEmpty()) {
            return 0;
        }
        for (ContractPaymentScheduleEntity row : overdueRows) {
            row.setStatus(ScheduleStatus.OVERDUE);
        }
        scheduleRepository.saveAll(overdueRows);
        return overdueRows.size();
    }

    @Override
    @Transactional(readOnly = true)
    public List<ContractPaymentScheduleResponse> getContractSchedules(Long contractId) {
        if (contractId == null) {
            return List.of();
        }
        return scheduleRepository.findByContractIdAndDeletedFalseOrderByPeriodNumberAsc(contractId)
                .stream()
                .map(ContractPaymentScheduleResponse::fromEntity)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public ContractSettlementPreviewResponse previewSettlement(ContractEntity contract, LocalDate asOfDate) {
        if (contract == null) {
            return null;
        }
        LocalDate effectiveDate = asOfDate != null ? asOfDate : LocalDate.now(clock);
        return buildPreviewWithPrincipal(contract, resolveEffectivePrincipal(contract), effectiveDate);
    }

    @Override
    @Transactional(readOnly = true)
    public void enrichContractResponse(ContractResponse response) {
        if (response == null || response.getId() == null) {
            return;
        }
        List<ContractPaymentScheduleEntity> schedules = scheduleRepository
                .findByContractIdAndDeletedFalseOrderByPeriodNumberAsc(response.getId());
        applySummaryToResponse(response, schedules);
    }

    @Override
    @Transactional(readOnly = true)
    public void enrichContractResponses(List<ContractResponse> responses) {
        if (responses == null || responses.isEmpty()) {
            return;
        }
        List<Long> ids = responses.stream()
                .map(ContractResponse::getId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (ids.isEmpty()) {
            return;
        }
        Map<Long, List<ContractPaymentScheduleEntity>> byContractId = scheduleRepository
                .findByContractIdInAndDeletedFalseOrderByContractIdAscPeriodNumberAsc(ids)
                .stream()
                .collect(Collectors.groupingBy(ContractPaymentScheduleEntity::getContractId));

        for (ContractResponse response : responses) {
            List<ContractPaymentScheduleEntity> rows = byContractId.getOrDefault(response.getId(), List.of());
            applySummaryToResponse(response, rows);
        }
    }

    private void handlePeriodicInterestCollection(
            ContractEntity contract,
            InterestPaymentEntity savedPayment,
            List<ContractPaymentScheduleEntity> rows,
            BigDecimal remainingPrincipal,
            LocalDate paymentDate
    ) {
        int cycleDays = positiveOrDefault(contract.getPaymentCycleDays(), DEFAULT_CYCLE_DAYS);
        BigDecimal paidInt = nvl(savedPayment.getInterestAmount());
        BigDecimal paidSto = nvl(savedPayment.getStorageFeeAmount());
        BigDecimal paidPen = nvl(savedPayment.getPenaltyAmount());
        BigDecimal paidTotal = paidInt.add(paidSto).add(paidPen);
        LocalDateTime settledAt = savedPayment.getPaymentDate() != null
                ? savedPayment.getPaymentDate()
                : LocalDateTime.now(clock);

        ContractPaymentScheduleEntity target = rows.stream()
                .filter(r -> isOpenStatus(r.getStatus()))
                .findFirst()
                .orElse(null);

        List<ContractPaymentScheduleEntity> toSave = new ArrayList<>();
        if (target != null) {
            target.setPaidInterestAmount(paidInt);
            target.setPaidStorageFeeAmount(paidSto);
            target.setPaidPenaltyAmount(paidPen);
            target.setPaidTotalAmount(paidTotal);
            target.setStatus(ScheduleStatus.PAID);
            target.setSettledAt(settledAt);
            target.setInterestPayment(savedPayment);
            savedPayment.setSchedule(target);
            toSave.add(target);

            ContractPaymentScheduleEntity nextOpen = rows.stream()
                    .filter(r -> !Objects.equals(r.getPeriodNumber(), target.getPeriodNumber()))
                    .filter(r -> isOpenStatus(r.getStatus()))
                    .findFirst()
                    .orElse(null);

            if (nextOpen != null) {
                // Keep fixed cycle anchors intact when paying on time or early
                contract.setNextInterestDueDate(nextOpen.getDueDate());
            } else if (remainingPrincipal.compareTo(BigDecimal.ZERO) > 0) {
                target.setFinalPeriod(Boolean.FALSE);
                ContractPaymentScheduleEntity extensionRow = createExtensionRow(
                        contract,
                        rows,
                        target.getToDate(),
                        cycleDays,
                        remainingPrincipal,
                        "Tự động gia hạn thêm 1 kỳ do đã đóng hết các kỳ dự kiến nhưng vẫn còn dư nợ gốc"
                );
                toSave.add(extensionRow);
                contract.setMaturityDate(extensionRow.getToDate());
                contract.setNextInterestDueDate(extensionRow.getDueDate());
                contract.setStatus(ContractStatus.EXTENDED);
            }
        } else if (remainingPrincipal.compareTo(BigDecimal.ZERO) > 0) {
            LocalDate anchorStart = findPaidThroughDate(rows, contract.getStartDate());
            ContractPaymentScheduleEntity settledExtension = createExtensionRow(
                    contract, rows, anchorStart, cycleDays, remainingPrincipal,
                    "Kỳ gia hạn thu lãi thực tế phát sinh");
            settledExtension.setPaidInterestAmount(paidInt);
            settledExtension.setPaidStorageFeeAmount(paidSto);
            settledExtension.setPaidPenaltyAmount(paidPen);
            settledExtension.setPaidTotalAmount(paidTotal);
            settledExtension.setStatus(ScheduleStatus.PAID);
            settledExtension.setFinalPeriod(Boolean.FALSE);
            settledExtension.setSettledAt(settledAt);
            settledExtension.setInterestPayment(savedPayment);
            savedPayment.setSchedule(settledExtension);
            toSave.add(settledExtension);

            List<ContractPaymentScheduleEntity> expandedRows = new ArrayList<>(rows);
            expandedRows.add(settledExtension);
            ContractPaymentScheduleEntity nextExtension = createExtensionRow(
                    contract,
                    expandedRows,
                    settledExtension.getToDate(),
                    cycleDays,
                    remainingPrincipal,
                    "Tự động gia hạn thêm 1 kỳ do hợp đồng vẫn còn dư nợ gốc"
            );
            toSave.add(nextExtension);
            contract.setMaturityDate(nextExtension.getToDate());
            contract.setNextInterestDueDate(nextExtension.getDueDate());
            contract.setStatus(ContractStatus.EXTENDED);
        }

        if (!toSave.isEmpty()) {
            scheduleRepository.saveAll(toSave);
        }
        log.debug("Applied periodic interest collection to schedule: contractId={}, period={}, paymentDate={}",
                contract.getId(), savedPayment.getSchedule() != null ? savedPayment.getSchedule().getPeriodNumber() : null,
                paymentDate);
    }

    private void handlePartialPrincipalCollection(
            ContractEntity contract,
            InterestPaymentEntity savedPayment,
            List<ContractPaymentScheduleEntity> rows,
            BigDecimal oldPrincipal,
            BigDecimal newPrincipal,
            LocalDate effectiveDate
    ) {
        int cycleDays = positiveOrDefault(contract.getPaymentCycleDays(), DEFAULT_CYCLE_DAYS);
        BigDecimal paidInt = nvl(savedPayment.getInterestAmount());
        BigDecimal paidSto = nvl(savedPayment.getStorageFeeAmount());
        BigDecimal paidPen = nvl(savedPayment.getPenaltyAmount());
        BigDecimal paidInterestAndFees = paidInt.add(paidSto).add(paidPen);
        LocalDateTime settledAt = savedPayment.getPaymentDate() != null
                ? savedPayment.getPaymentDate()
                : LocalDateTime.now(clock);

        LocalDate paidThrough = findPaidThroughDate(rows, contract.getStartDate());
        if (paidThrough == null) {
            paidThrough = effectiveDate;
        }

        List<ContractPaymentScheduleEntity> openRows = rows.stream()
                .filter(r -> isOpenStatus(r.getStatus()))
                .collect(Collectors.toCollection(ArrayList::new));
        int maxPeriod = rows.stream().mapToInt(ContractPaymentScheduleEntity::getPeriodNumber).max().orElse(0);

        List<ContractPaymentScheduleEntity> toSave = new ArrayList<>();
        LocalDate cutoffDate;
        int nextStartPeriod;

        boolean hasUnpaidElapsedDays = effectiveDate.isAfter(paidThrough);
        boolean collectedInterestOrFees = paidInterestAndFees.compareTo(BigDecimal.ZERO) > 0;

        if (hasUnpaidElapsedDays || collectedInterestOrFees) {
            ContractPaymentScheduleEntity settledRow;
            if (!openRows.isEmpty()) {
                settledRow = openRows.remove(0);
            } else {
                settledRow = ContractPaymentScheduleEntity.builder()
                        .contract(contract)
                        .periodNumber(++maxPeriod)
                        .build();
            }

            LocalDate settledEnd = effectiveDate.isAfter(paidThrough) ? effectiveDate : paidThrough;
            int elapsedDays = (int) Math.max(0, ChronoUnit.DAYS.between(paidThrough, settledEnd));
            CycleBreakdown elapsedExpected = computeCycleBreakdown(
                    oldPrincipal,
                    contract.getInterestType(),
                    contract.getInterestRate(),
                    contract.getStorageFeeRate(),
                    elapsedDays
            );

            settledRow.setFromDate(paidThrough);
            settledRow.setToDate(settledEnd);
            settledRow.setDueDate(settledEnd);
            settledRow.setDaysCount(elapsedDays);
            settledRow.setPrincipalBase(oldPrincipal);
            settledRow.setExpectedInterestAmount(elapsedExpected.interest());
            settledRow.setExpectedStorageFeeAmount(elapsedExpected.storageFee());
            settledRow.setExpectedTotalAmount(elapsedExpected.total());
            settledRow.setPaidInterestAmount(paidInt);
            settledRow.setPaidStorageFeeAmount(paidSto);
            settledRow.setPaidPenaltyAmount(paidPen);
            settledRow.setPaidTotalAmount(paidInterestAndFees);
            settledRow.setStatus(ScheduleStatus.PAID);
            settledRow.setFinalPeriod(Boolean.FALSE);
            settledRow.setSettledAt(settledAt);
            settledRow.setInterestPayment(savedPayment);
            settledRow.setAdjustmentNote("Chốt lãi thực tế (" + elapsedDays
                    + " ngày trên dư nợ " + formatVndCompact(oldPrincipal)
                    + ") khi trả bớt gốc ngày " + effectiveDate);

            toSave.add(settledRow);
            savedPayment.setSchedule(settledRow);

            cutoffDate = settledEnd;
            nextStartPeriod = settledRow.getPeriodNumber() + 1;
        } else {
            // Pure principal reduction on boundary/day-0: do not mutate previously PAID rows
            cutoffDate = paidThrough;
            nextStartPeriod = openRows.isEmpty() ? maxPeriod + 1 : openRows.get(0).getPeriodNumber();
        }

        if (newPrincipal.compareTo(BigDecimal.ZERO) <= 0) {
            for (ContractPaymentScheduleEntity remainingOpen : openRows) {
                zeroAndCancelRow(remainingOpen, "Hủy kỳ do khách đã trả hết gốc ngày " + effectiveDate);
                toSave.add(remainingOpen);
            }
            scheduleRepository.saveAll(toSave);
            return;
        }

        LocalDate targetMaturity = contract.getMaturityDate();
        if (targetMaturity == null || !cutoffDate.isBefore(targetMaturity)) {
            targetMaturity = cutoffDate.plusDays(cycleDays);
            contract.setMaturityDate(targetMaturity);
        }

        Map<Integer, ContractPaymentScheduleEntity> openByPeriod = indexByPeriod(openRows);
        List<DateSlice> newSlices = buildDateSlices(cutoffDate, targetMaturity, cycleDays, nextStartPeriod);
        String recalcNote = "Tính lại lãi trên dư nợ mới (" + formatVndCompact(newPrincipal)
                + ") từ ngày " + cutoffDate;

        List<ContractPaymentScheduleEntity> regenerated = new ArrayList<>();
        for (DateSlice slice : newSlices) {
            CycleBreakdown breakdown = computeCycleBreakdown(
                    newPrincipal,
                    contract.getInterestType(),
                    contract.getInterestRate(),
                    contract.getStorageFeeRate(),
                    slice.daysCount()
            );
            ContractPaymentScheduleEntity row = openByPeriod.remove(slice.periodNumber());
            if (row == null) {
                row = ContractPaymentScheduleEntity.builder()
                        .contract(contract)
                        .periodNumber(slice.periodNumber())
                        .build();
            }
            ScheduleStatus nextStatus = slice.toDate().isBefore(effectiveDate)
                    ? ScheduleStatus.OVERDUE
                    : ScheduleStatus.SCHEDULED;
            applyPlannedSlice(row, slice, newPrincipal, breakdown, nextStatus, recalcNote);
            regenerated.add(row);
            toSave.add(row);
        }

        for (ContractPaymentScheduleEntity leftoverOpen : openByPeriod.values()) {
            zeroAndCancelRow(leftoverOpen, "Điều chỉnh lại số kỳ sau trả bớt gốc ngày " + effectiveDate);
            toSave.add(leftoverOpen);
        }

        scheduleRepository.saveAll(toSave);
        if (!regenerated.isEmpty()) {
            contract.setNextInterestDueDate(regenerated.get(0).getDueDate());
        }
    }

    private ContractSettlementPreviewResponse buildPreviewWithPrincipal(
            ContractEntity contract,
            BigDecimal remainingPrincipal,
            LocalDate effectiveDate
    ) {
        int cycleDays = positiveOrDefault(contract.getPaymentCycleDays(), DEFAULT_CYCLE_DAYS);
        List<ContractPaymentScheduleEntity> persistedRows = contract.getId() != null
                ? scheduleRepository.findByContractIdAndDeletedFalseOrderByPeriodNumberAsc(contract.getId())
                : List.of();

        List<ContractPaymentScheduleResponse> scheduleResponses = persistedRows.isEmpty()
                ? buildSyntheticScheduleResponses(contract, remainingPrincipal, cycleDays)
                : persistedRows.stream().map(ContractPaymentScheduleResponse::fromEntity).toList();

        List<ContractPaymentScheduleResponse> openRows = scheduleResponses.stream()
                .filter(r -> isOpenStatus(r.getStatus()))
                .toList();
        int paidCount = (int) scheduleResponses.stream()
                .filter(r -> isSettledStatus(r.getStatus()))
                .count();
        ContractPaymentScheduleResponse currentOpen = openRows.isEmpty() ? null : openRows.get(0);
        ContractPaymentScheduleResponse nextOpenAfterCurrent = openRows.size() > 1 ? openRows.get(1) : null;

        LocalDate paidThrough = findPaidThroughDateFromResponses(scheduleResponses, contract.getStartDate());
        boolean hasAnyPaidCycle = scheduleResponses.stream().anyMatch(r -> r.getStatus() == ScheduleStatus.PAID);

        int elapsedDays = 0;
        if (paidThrough != null) {
            long rawDays = ChronoUnit.DAYS.between(paidThrough, effectiveDate);
            if (rawDays > 0) {
                elapsedDays = (int) rawDays;
            } else if (!hasAnyPaidCycle && effectiveDate.equals(paidThrough)) {
                elapsedDays = 1;
            }
        }

        CycleBreakdown proRata = computeCycleBreakdown(
                remainingPrincipal,
                contract.getInterestType(),
                contract.getInterestRate(),
                contract.getStorageFeeRate(),
                elapsedDays
        );

        int cancelledPeriodsCount = (elapsedDays > 0 && !openRows.isEmpty())
                ? Math.max(0, openRows.size() - 1)
                : openRows.size();

        LocalDate nextPeriodDueDate;
        if (nextOpenAfterCurrent != null) {
            nextPeriodDueDate = nextOpenAfterCurrent.getDueDate();
        } else if (currentOpen != null && remainingPrincipal.compareTo(BigDecimal.ZERO) > 0) {
            nextPeriodDueDate = currentOpen.getToDate().plusDays(cycleDays);
        } else {
            nextPeriodDueDate = contract.getNextInterestDueDate();
        }

        String adjustmentSummary = buildRedemptionSummary(
                paidThrough, effectiveDate, elapsedDays, hasAnyPaidCycle, cancelledPeriodsCount);

        return ContractSettlementPreviewResponse.builder()
                .contractId(contract.getId())
                .contractCode(contract.getContractCode())
                .status(contract.getStatus())
                .asOfDate(effectiveDate)
                .remainingPrincipal(remainingPrincipal)
                .interestType(contract.getInterestType())
                .interestRate(nvl(contract.getInterestRate()))
                .storageFeeRate(nvl(contract.getStorageFeeRate()))
                .paymentCycleDays(cycleDays)
                .paidThroughDate(paidThrough)
                .nextDueDate(currentOpen != null ? currentOpen.getDueDate() : contract.getNextInterestDueDate())
                .maturityDate(contract.getMaturityDate())
                .currentPeriodNumber(currentOpen != null ? currentOpen.getPeriodNumber() : null)
                .currentPeriodFromDate(currentOpen != null ? currentOpen.getFromDate() : null)
                .currentPeriodToDate(currentOpen != null ? currentOpen.getToDate() : null)
                .currentPeriodDueDate(currentOpen != null ? currentOpen.getDueDate() : null)
                .currentPeriodStatus(currentOpen != null ? currentOpen.getStatus() : null)
                .currentPeriodExpectedInterest(currentOpen != null ? currentOpen.getExpectedInterestAmount() : BigDecimal.ZERO)
                .currentPeriodExpectedStorageFee(currentOpen != null ? currentOpen.getExpectedStorageFeeAmount() : BigDecimal.ZERO)
                .currentPeriodExpectedTotal(currentOpen != null ? currentOpen.getExpectedTotalAmount() : BigDecimal.ZERO)
                .nextPeriodDueDate(nextPeriodDueDate)
                .openPeriodsCount(openRows.size())
                .paidPeriodsCount(paidCount)
                .totalPeriodsCount(scheduleResponses.size())
                .redemptionElapsedDays(elapsedDays)
                .redemptionInterestAmount(proRata.interest())
                .redemptionStorageFeeAmount(proRata.storageFee())
                .redemptionPenaltyAmount(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP))
                .redemptionTotalInterestAndFees(proRata.total())
                .redemptionTotalSettlement(remainingPrincipal.add(proRata.total()))
                .redemptionCancelledPeriodsCount(cancelledPeriodsCount)
                .redemptionAdjustmentSummary(adjustmentSummary)
                .schedules(scheduleResponses)
                .build();
    }

    private List<ContractPaymentScheduleResponse> buildSyntheticScheduleResponses(
            ContractEntity contract,
            BigDecimal principal,
            int cycleDays
    ) {
        LocalDate start = contract.getStartDate() != null ? contract.getStartDate() : LocalDate.now(clock);
        int termDays = positiveOrDefault(contract.getLoanTermDays(), DEFAULT_LOAN_TERM_DAYS);
        LocalDate maturity = contract.getMaturityDate() != null ? contract.getMaturityDate() : start.plusDays(termDays);
        ScheduleStatus fallbackStatus = contract.getStatus() == ContractStatus.PENDING_APPROVAL
                ? ScheduleStatus.PROJECTED
                : (contract.getStatus() == ContractStatus.OVERDUE ? ScheduleStatus.OVERDUE : ScheduleStatus.SCHEDULED);

        List<ContractPaymentScheduleResponse> synthetic = new ArrayList<>();
        for (DateSlice slice : buildDateSlices(start, maturity, cycleDays, 1)) {
            CycleBreakdown breakdown = computeCycleBreakdown(
                    principal,
                    contract.getInterestType(),
                    contract.getInterestRate(),
                    contract.getStorageFeeRate(),
                    slice.daysCount()
            );
            synthetic.add(ContractPaymentScheduleResponse.builder()
                    .contractId(contract.getId())
                    .periodNumber(slice.periodNumber())
                    .fromDate(slice.fromDate())
                    .toDate(slice.toDate())
                    .dueDate(slice.toDate())
                    .daysCount(slice.daysCount())
                    .principalBase(principal)
                    .expectedInterestAmount(breakdown.interest())
                    .expectedStorageFeeAmount(breakdown.storageFee())
                    .expectedTotalAmount(breakdown.total())
                    .paidInterestAmount(BigDecimal.ZERO)
                    .paidStorageFeeAmount(BigDecimal.ZERO)
                    .paidPenaltyAmount(BigDecimal.ZERO)
                    .paidTotalAmount(BigDecimal.ZERO)
                    .status(fallbackStatus)
                    .finalPeriod(slice.finalPeriod())
                    .build());
        }
        return synthetic;
    }

    private void applySummaryToResponse(ContractResponse response, List<ContractPaymentScheduleEntity> rows) {
        List<ContractPaymentScheduleResponse> dtos = rows.stream()
                .map(ContractPaymentScheduleResponse::fromEntity)
                .toList();
        int paidCount = (int) rows.stream().filter(r -> isSettledStatus(r.getStatus())).count();
        Integer currentPeriod = rows.stream()
                .filter(r -> isOpenStatus(r.getStatus()))
                .map(ContractPaymentScheduleEntity::getPeriodNumber)
                .findFirst()
                .orElse(null);

        response.setTotalPeriodsCount(dtos.size());
        response.setPaidPeriodsCount(paidCount);
        response.setCurrentPeriodNumber(currentPeriod);
        response.setPaymentSchedules(dtos);
    }

    private ContractPaymentScheduleEntity createExtensionRow(
            ContractEntity contract,
            List<ContractPaymentScheduleEntity> existingRows,
            LocalDate fromDate,
            int cycleDays,
            BigDecimal principalBase,
            String adjustmentNote
    ) {
        int nextPeriod = existingRows.stream()
                .mapToInt(ContractPaymentScheduleEntity::getPeriodNumber)
                .max()
                .orElse(0) + 1;
        LocalDate effectiveFrom = fromDate != null ? fromDate : LocalDate.now(clock);
        LocalDate effectiveTo = effectiveFrom.plusDays(cycleDays);
        CycleBreakdown breakdown = computeCycleBreakdown(
                principalBase,
                contract.getInterestType(),
                contract.getInterestRate(),
                contract.getStorageFeeRate(),
                cycleDays
        );
        return ContractPaymentScheduleEntity.builder()
                .contract(contract)
                .periodNumber(nextPeriod)
                .fromDate(effectiveFrom)
                .toDate(effectiveTo)
                .dueDate(effectiveTo)
                .daysCount(cycleDays)
                .principalBase(principalBase)
                .expectedInterestAmount(breakdown.interest())
                .expectedStorageFeeAmount(breakdown.storageFee())
                .expectedTotalAmount(breakdown.total())
                .paidInterestAmount(BigDecimal.ZERO)
                .paidStorageFeeAmount(BigDecimal.ZERO)
                .paidPenaltyAmount(BigDecimal.ZERO)
                .paidTotalAmount(BigDecimal.ZERO)
                .status(ScheduleStatus.SCHEDULED)
                .finalPeriod(Boolean.TRUE)
                .adjustmentNote(adjustmentNote)
                .build();
    }

    static CycleBreakdown computeCycleBreakdown(
            BigDecimal principalBase,
            InterestType interestType,
            BigDecimal interestRate,
            BigDecimal storageFeeRate,
            int daysCount
    ) {
        BigDecimal principal = nvl(principalBase);
        if (daysCount <= 0 || principal.compareTo(BigDecimal.ZERO) <= 0) {
            BigDecimal zero = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
            return new CycleBreakdown(zero, zero, zero);
        }

        BigDecimal rate = nvl(interestRate);
        BigDecimal storageRate = nvl(storageFeeRate);
        BigDecimal days = BigDecimal.valueOf(daysCount);
        InterestType effectiveType = interestType != null ? interestType : InterestType.DAILY_PER_MILLION;

        BigDecimal rawInterest;
        BigDecimal rawStorage;
        switch (effectiveType) {
            case DAILY_PER_MILLION -> {
                BigDecimal millionUnits = principal.divide(MILLION, 10, RoundingMode.HALF_UP);
                rawInterest = millionUnits.multiply(rate).multiply(days);
                rawStorage = millionUnits.multiply(storageRate).multiply(days);
            }
            case PERCENT_MONTHLY -> {
                rawInterest = principal.multiply(rate)
                        .divide(HUNDRED, 10, RoundingMode.HALF_UP)
                        .multiply(days)
                        .divide(THIRTY, 10, RoundingMode.HALF_UP);
                rawStorage = principal.multiply(storageRate)
                        .divide(HUNDRED, 10, RoundingMode.HALF_UP)
                        .multiply(days)
                        .divide(THIRTY, 10, RoundingMode.HALF_UP);
            }
            case PERCENT_WEEKLY -> {
                rawInterest = principal.multiply(rate)
                        .divide(HUNDRED, 10, RoundingMode.HALF_UP)
                        .multiply(days)
                        .divide(SEVEN, 10, RoundingMode.HALF_UP);
                rawStorage = principal.multiply(storageRate)
                        .divide(HUNDRED, 10, RoundingMode.HALF_UP)
                        .multiply(days)
                        .divide(SEVEN, 10, RoundingMode.HALF_UP);
            }
            default -> {
                rawInterest = BigDecimal.ZERO;
                rawStorage = BigDecimal.ZERO;
            }
        }

        BigDecimal roundedInterest = rawInterest.setScale(0, RoundingMode.HALF_UP).setScale(2, RoundingMode.HALF_UP);
        BigDecimal roundedStorage = rawStorage.setScale(0, RoundingMode.HALF_UP).setScale(2, RoundingMode.HALF_UP);
        return new CycleBreakdown(roundedInterest, roundedStorage, roundedInterest.add(roundedStorage));
    }

    private List<DateSlice> buildDateSlices(LocalDate start, LocalDate end, int cycleDays, int startPeriodNum) {
        List<DateSlice> slices = new ArrayList<>();
        if (start == null || end == null || !start.isBefore(end)) {
            return slices;
        }
        LocalDate cursor = start;
        int periodNum = startPeriodNum;
        while (cursor.isBefore(end)) {
            LocalDate next = cursor.plusDays(cycleDays);
            if (next.isAfter(end)) {
                next = end;
            }
            int days = (int) ChronoUnit.DAYS.between(cursor, next);
            boolean isFinal = !next.isBefore(end);
            slices.add(new DateSlice(periodNum++, cursor, next, days, isFinal));
            cursor = next;
        }
        return slices;
    }

    private Map<Integer, ContractPaymentScheduleEntity> indexByPeriod(List<ContractPaymentScheduleEntity> rows) {
        return rows.stream().collect(Collectors.toMap(
                ContractPaymentScheduleEntity::getPeriodNumber,
                Function.identity(),
                (a, b) -> a,
                LinkedHashMap::new
        ));
    }

    private ContractPaymentScheduleEntity upsertRow(
            Map<Integer, ContractPaymentScheduleEntity> existingByPeriod,
            ContractEntity contract,
            int periodNumber
    ) {
        ContractPaymentScheduleEntity existing = existingByPeriod.get(periodNumber);
        if (existing != null) {
            return existing;
        }
        return ContractPaymentScheduleEntity.builder()
                .contract(contract)
                .periodNumber(periodNumber)
                .build();
    }

    private void applyPlannedSlice(
            ContractPaymentScheduleEntity row,
            DateSlice slice,
            BigDecimal principalBase,
            CycleBreakdown breakdown,
            ScheduleStatus status,
            String note
    ) {
        BigDecimal zero = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        row.setFromDate(slice.fromDate());
        row.setToDate(slice.toDate());
        row.setDueDate(slice.toDate());
        row.setDaysCount(slice.daysCount());
        row.setPrincipalBase(principalBase);
        row.setExpectedInterestAmount(breakdown.interest());
        row.setExpectedStorageFeeAmount(breakdown.storageFee());
        row.setExpectedTotalAmount(breakdown.total());
        row.setPaidInterestAmount(zero);
        row.setPaidStorageFeeAmount(zero);
        row.setPaidPenaltyAmount(zero);
        row.setPaidTotalAmount(zero);
        row.setStatus(status);
        row.setFinalPeriod(slice.finalPeriod());
        row.setSettledAt(null);
        row.setInterestPayment(null);
        row.setAdjustmentNote(note);
        row.setDeleted(false);
    }

    private void cancelExtraExistingRows(
            List<ContractPaymentScheduleEntity> existing,
            int usedPeriods,
            List<ContractPaymentScheduleEntity> toSave,
            String note,
            boolean skipSettled
    ) {
        for (ContractPaymentScheduleEntity row : existing) {
            if (row.getPeriodNumber() > usedPeriods && (!skipSettled || !isSettledStatus(row.getStatus()))) {
                zeroAndCancelRow(row, note);
                toSave.add(row);
            }
        }
    }

    private void zeroAndCancelRow(ContractPaymentScheduleEntity row, String note) {
        BigDecimal zero = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        row.setStatus(ScheduleStatus.CANCELLED);
        row.setExpectedInterestAmount(zero);
        row.setExpectedStorageFeeAmount(zero);
        row.setExpectedTotalAmount(zero);
        row.setFinalPeriod(Boolean.FALSE);
        row.setAdjustmentNote(note);
    }

    private LocalDate findPaidThroughDate(List<ContractPaymentScheduleEntity> rows, LocalDate fallbackStart) {
        return rows.stream()
                .filter(r -> isSettledStatus(r.getStatus()))
                .map(ContractPaymentScheduleEntity::getToDate)
                .filter(Objects::nonNull)
                .max(LocalDate::compareTo)
                .orElse(fallbackStart);
    }

    private LocalDate findPaidThroughDateFromResponses(List<ContractPaymentScheduleResponse> rows, LocalDate fallbackStart) {
        return rows.stream()
                .filter(r -> isSettledStatus(r.getStatus()))
                .map(ContractPaymentScheduleResponse::getToDate)
                .filter(Objects::nonNull)
                .max(LocalDate::compareTo)
                .orElse(fallbackStart);
    }

    private String buildRedemptionSummary(
            LocalDate paidThrough,
            LocalDate effectiveDate,
            int elapsedDays,
            boolean hasAnyPaidCycle,
            int cancelledCount
    ) {
        if (paidThrough == null) {
            return "Tính lãi tất toán thực tế đến ngày " + effectiveDate + ".";
        }
        if (elapsedDays == 0) {
            return "Hợp đồng đã đóng lãi tới hết ngày " + paidThrough
                    + " (vượt ngày tất toán " + effectiveDate + "), miễn thêm lãi kỳ mới và hủy "
                    + cancelledCount + " kỳ tương lai.";
        }
        if (!hasAnyPaidCycle && effectiveDate.equals(paidThrough)) {
            return "Tất toán ngay trong ngày mở/giải ngân (" + effectiveDate
                    + "): tính tối thiểu 1 ngày lãi & phí lưu kho; hủy " + cancelledCount + " kỳ tương lai.";
        }
        return "Tính " + elapsedDays + " ngày lãi & phí thực tế (từ " + paidThrough
                + " đến " + effectiveDate + "); chốt kỳ hiện tại và hủy " + cancelledCount + " kỳ còn lại.";
    }

    private static boolean isOpenStatus(ScheduleStatus status) {
        return status == ScheduleStatus.PROJECTED
                || status == ScheduleStatus.SCHEDULED
                || status == ScheduleStatus.OVERDUE;
    }

    private static boolean isSettledStatus(ScheduleStatus status) {
        return status == ScheduleStatus.PAID || status == ScheduleStatus.SETTLED_EARLY;
    }

    private static int positiveOrDefault(Integer val, int fallback) {
        return val != null && val > 0 ? val : fallback;
    }

    private static BigDecimal resolveEffectivePrincipal(ContractEntity contract) {
        if (contract.getRemainingPrincipal() != null) {
            return contract.getRemainingPrincipal();
        }
        return nvl(contract.getPrincipalAmount());
    }

    private static BigDecimal nvl(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
    }

    private static String formatVndCompact(BigDecimal amount) {
        NumberFormat nf = NumberFormat.getNumberInstance(Locale.US);
        nf.setMaximumFractionDigits(0);
        return nf.format(nvl(amount)) + " ₫";
    }

    record DateSlice(int periodNumber, LocalDate fromDate, LocalDate toDate, int daysCount, boolean finalPeriod) {
    }

    record CycleBreakdown(BigDecimal interest, BigDecimal storageFee, BigDecimal total) {
    }
}

