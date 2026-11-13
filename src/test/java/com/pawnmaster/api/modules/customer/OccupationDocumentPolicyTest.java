package com.pawnmaster.api.modules.customer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pawnmaster.api.common.BusinessException;
import com.pawnmaster.api.common.ErrorCode;
import com.pawnmaster.api.common.GlobalExceptionHandler;
import com.pawnmaster.api.config.SecurityConfig;
import com.pawnmaster.api.modules.branch.BranchEntity;
import com.pawnmaster.api.modules.branch.BranchRepository;
import com.pawnmaster.api.modules.cashbook.CashbookService;
import com.pawnmaster.api.modules.collateral.CollateralCategory;
import com.pawnmaster.api.modules.collateral.CollateralEntity;
import com.pawnmaster.api.modules.collateral.CollateralRepository;
import com.pawnmaster.api.modules.collateral.CollateralStatus;
import com.pawnmaster.api.modules.collateral.LtvPolicyService;
import com.pawnmaster.api.modules.contract.ContractEntity;
import com.pawnmaster.api.modules.contract.ContractRepository;
import com.pawnmaster.api.modules.contract.ContractServiceImpl;
import com.pawnmaster.api.modules.contract.DisbursementMethod;
import com.pawnmaster.api.modules.contract.InterestPaymentRepository;
import com.pawnmaster.api.modules.contract.InterestType;
import com.pawnmaster.api.modules.contract.dto.ContractCreateRequest;
import com.pawnmaster.api.modules.contract.dto.ContractResponse;
import com.pawnmaster.api.modules.contract.dto.QuickContractOriginationRequest;
import com.pawnmaster.api.modules.customer.dto.CustomerCreateRequest;
import com.pawnmaster.api.modules.customer.dto.CustomerResponse;
import com.pawnmaster.api.modules.customer.dto.CustomerUpdateRequest;
import com.pawnmaster.api.modules.customer.dto.OccupationDocumentPolicyItemDto;
import com.pawnmaster.api.modules.customer.dto.UpsertOccupationDocPolicyRequest;
import com.pawnmaster.api.modules.customer.dto.UpsertVerificationDocTypeRequest;
import com.pawnmaster.api.modules.user.UserRepository;
import com.pawnmaster.api.security.JwtAuthenticationFilter;
import com.pawnmaster.api.security.JwtTokenProvider;
import com.pawnmaster.api.security.UserDetailsServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("DB-Driven Occupation x Verification Document Policy Tests")
class OccupationDocumentPolicyTest {

    @Nested
    @DisplayName("1. Domain Policy, CustomerService & ContractService Integration (Q1 & Q4)")
    class PolicyAndServiceIntegrationTests {

        private VerificationDocumentTypeRepository docTypeRepository;
        private OccupationDocumentPolicyRepository policyRepository;
        private VerificationDocumentPolicy verificationDocumentPolicy;

        private CustomerRepository customerRepository;
        private BranchRepository branchRepository;
        private CollateralRepository collateralRepository;
        private ContractRepository contractRepository;
        private LtvPolicyService ltvPolicyService;

        private CustomerServiceImpl customerService;
        private ContractServiceImpl contractService;

        private final Map<String, VerificationDocumentTypeEntity> docTypeStore = new LinkedHashMap<>();
        private final Map<String, OccupationDocumentPolicyEntity> policyStore = new LinkedHashMap<>();
        private final Map<Long, CustomerEntity> customerStore = new LinkedHashMap<>();
        private final Map<Long, CollateralEntity> collateralStore = new LinkedHashMap<>();
        private final AtomicLong seq = new AtomicLong(100L);

        private BranchEntity sampleBranch;

        @BeforeEach
        void setUp() {
            docTypeStore.clear();
            policyStore.clear();
            customerStore.clear();
            collateralStore.clear();

            docTypeRepository = mock(VerificationDocumentTypeRepository.class);
            policyRepository = mock(OccupationDocumentPolicyRepository.class);
            customerRepository = mock(CustomerRepository.class);
            branchRepository = mock(BranchRepository.class);
            collateralRepository = mock(CollateralRepository.class);
            contractRepository = mock(ContractRepository.class);
            ltvPolicyService = mock(LtvPolicyService.class);

            wireStatefulDocTypeRepository();
            wireStatefulPolicyRepository();
            seedDefaultDbCatalogAndMatrix();

            verificationDocumentPolicy = new VerificationDocumentPolicy(docTypeRepository, policyRepository);
            customerService = new CustomerServiceImpl(customerRepository, verificationDocumentPolicy);

            contractService = new ContractServiceImpl(
                    contractRepository,
                    customerRepository,
                    collateralRepository,
                    branchRepository,
                    mock(UserRepository.class),
                    mock(CashbookService.class),
                    ltvPolicyService,
                    verificationDocumentPolicy,
                    mock(InterestPaymentRepository.class),
                    null, null, null, null, null, null
            );

            sampleBranch = BranchEntity.builder()
                    .branchCode("CN-HCM-01")
                    .branchName("Chi nhánh Quận 1")
                    .provinceCode("79")
                    .districtCode("760")
                    .currentCashBalance(new BigDecimal("500000000.00"))
                    .active(true)
                    .build();
            sampleBranch.setId(1L);

            lenient().when(branchRepository.findByIdAndDeletedFalse(1L)).thenReturn(Optional.of(sampleBranch));
            lenient().when(ltvPolicyService.getMaxLtvRatio(any(), any())).thenReturn(new BigDecimal("0.92"));

            lenient().when(customerRepository.findByIdentityNumberAndDeletedFalse(anyString())).thenAnswer(inv -> {
                String cccd = inv.getArgument(0);
                return customerStore.values().stream()
                        .filter(c -> !c.isDeleted() && cccd.equals(c.getIdentityNumber()))
                        .findFirst();
            });
            lenient().when(customerRepository.findByIdAndDeletedFalse(any(Long.class))).thenAnswer(inv -> {
                Long id = inv.getArgument(0);
                CustomerEntity found = customerStore.get(id);
                return (found != null && !found.isDeleted()) ? Optional.of(found) : Optional.empty();
            });
            lenient().when(customerRepository.findById(any(Long.class))).thenAnswer(inv -> {
                Long id = inv.getArgument(0);
                return Optional.ofNullable(customerStore.get(id));
            });
            lenient().when(customerRepository.save(any(CustomerEntity.class))).thenAnswer(inv -> {
                CustomerEntity c = inv.getArgument(0);
                if (c.getId() == null) {
                    c.setId(seq.incrementAndGet());
                }
                customerStore.put(c.getId(), c);
                return c;
            });

            lenient().when(collateralRepository.findByIdAndDeletedFalse(any(Long.class))).thenAnswer(inv -> {
                Long id = inv.getArgument(0);
                CollateralEntity found = collateralStore.get(id);
                return (found != null && !found.isDeleted()) ? Optional.of(found) : Optional.empty();
            });
            lenient().when(collateralRepository.save(any(CollateralEntity.class))).thenAnswer(inv -> {
                CollateralEntity col = inv.getArgument(0);
                if (col.getId() == null) {
                    col.setId(seq.incrementAndGet());
                }
                collateralStore.put(col.getId(), col);
                return col;
            });
            lenient().when(contractRepository.save(any(ContractEntity.class))).thenAnswer(inv -> {
                ContractEntity contract = inv.getArgument(0);
                if (contract.getId() == null) {
                    contract.setId(seq.incrementAndGet());
                }
                return contract;
            });
        }

        @Test
        @DisplayName("Custom DB document type (TAX_RETURN_PIT) mapped via Admin API works across Customer & Contract origination without code changes")
        void adminAddsCustomDocTypeAndMapsToOccupation_AcceptedByCustomerAndContractServices() {
            verificationDocumentPolicy.upsertDocumentType(UpsertVerificationDocTypeRequest.builder()
                    .code("TAX_RETURN_PIT")
                    .name("Tờ khai quyết toán thuế TNCN")
                    .docCategory("INCOME")
                    .description("Quyết toán thuế TNCN có xác nhận cơ quan thuế")
                    .displayOrder(20)
                    .active(true)
                    .build());

            OccupationDocumentPolicyItemDto mapped = verificationDocumentPolicy.upsertOccupationDocumentPolicy(
                    UpsertOccupationDocPolicyRequest.builder()
                            .occupationGroup(CustomerOccupationGroup.OFFICE_WORKER)
                            .docCode("TAX_RETURN_PIT")
                            .isDefault(false)
                            .displayOrder(4)
                            .active(true)
                            .build());

            assertEquals("TAX_RETURN_PIT", mapped.getDocCode());
            assertTrue(mapped.isActive());
            assertTrue(verificationDocumentPolicy.isDocumentCodeValidForGroup(
                    CustomerOccupationGroup.OFFICE_WORKER, "TAX_RETURN_PIT"));

            CustomerCreateRequest createReq = CustomerCreateRequest.builder()
                    .fullName("Nguyễn Thị Thuế")
                    .identityNumber("079092004455")
                    .phoneNumber("0903444555")
                    .address("12 Nguyễn Huệ, Bến Nghé, Quận 1")
                    .occupationGroup(CustomerOccupationGroup.OFFICE_WORKER)
                    .monthlyIncome(new BigDecimal("25000000"))
                    .hasSocialInsurance(true)
                    .verificationDocType("TAX_RETURN_PIT")
                    .verificationDocUrls(List.of("https://cdn.pawnmaster.vn/tax_pit.pdf"))
                    .build();

            CustomerResponse created = customerService.createCustomer(createReq);
            assertEquals("TAX_RETURN_PIT", created.getVerificationDocType());
            assertEquals("Tờ khai quyết toán thuế TNCN", created.getVerificationDocTypeDescription());

            QuickContractOriginationRequest quickReq = QuickContractOriginationRequest.builder()
                    .branchId(1L)
                    .customerFullName("Lê Hoàng Thuế")
                    .identityNumber("079092007788")
                    .phoneNumber("0908777666")
                    .occupationGroup(CustomerOccupationGroup.OFFICE_WORKER)
                    .monthlyIncome(new BigDecimal("22000000"))
                    .verificationDocType("TAX_RETURN_PIT")
                    .verificationDocUrls("https://cdn.pawnmaster.vn/tax_pit_2.pdf")
                    .collateralCategory(CollateralCategory.MOTORBIKE)
                    .collateralName("Honda SH 160i")
                    .appraisedValue(new BigDecimal("80000000"))
                    .principalAmount(new BigDecimal("50000000"))
                    .interestType(InterestType.DAILY_PER_MILLION)
                    .interestRate(new BigDecimal("2500"))
                    .disbursementMethod(DisbursementMethod.CASH)
                    .build();

            ContractResponse originated = contractService.originateFullContract(quickReq, 99L);
            assertNotNull(originated);
            CustomerEntity savedOrigCust = customerStore.values().stream()
                    .filter(c -> "079092007788".equals(c.getIdentityNumber()))
                    .findFirst()
                    .orElseThrow();
            assertEquals("TAX_RETURN_PIT", savedOrigCust.getVerificationDocType());
        }

        @Test
        @DisplayName("Q4: Deactivating mapping blocks new customers and KYC changes, while preserving historical verified customers for new contracts")
        void deactivatedDocMappingBlocksNewDossiersButPreservesHistoricalCustomerContracts() {
            // 1. Existing customer verified cleanly while LABOR_CONTRACT is active for OFFICE_WORKER
            CustomerResponse existingCustomer = customerService.createCustomer(CustomerCreateRequest.builder()
                    .fullName("Phạm Văn Lịch Sử")
                    .identityNumber("079088001122")
                    .phoneNumber("0901112233")
                    .address("10 Lê Lợi, Bến Nghé, Quận 1")
                    .occupationGroup(CustomerOccupationGroup.OFFICE_WORKER)
                    .monthlyIncome(new BigDecimal("18000000"))
                    .hasSocialInsurance(true)
                    .verificationDocType("LABOR_CONTRACT")
                    .verificationDocUrls(List.of("https://cdn.pawnmaster.vn/hdld_old.jpg"))
                    .build());

            // 2. Admin deactivates LABOR_CONTRACT mapping for OFFICE_WORKER
            verificationDocumentPolicy.upsertOccupationDocumentPolicy(UpsertOccupationDocPolicyRequest.builder()
                    .occupationGroup(CustomerOccupationGroup.OFFICE_WORKER)
                    .docCode("LABOR_CONTRACT")
                    .isDefault(false)
                    .active(false)
                    .build());

            assertFalse(verificationDocumentPolicy.isDocumentCodeValidForGroup(
                    CustomerOccupationGroup.OFFICE_WORKER, "LABOR_CONTRACT"));

            // 3a. Creating a NEW customer with LABOR_CONTRACT must now fail with INVALID_VERIFICATION_DOCUMENT
            CustomerCreateRequest blockedCreate = CustomerCreateRequest.builder()
                    .fullName("Khách Mới Bị Chặn")
                    .identityNumber("079099002233")
                    .phoneNumber("0909222333")
                    .address("20 Lê Duẩn, Quận 1")
                    .occupationGroup(CustomerOccupationGroup.OFFICE_WORKER)
                    .monthlyIncome(new BigDecimal("15000000"))
                    .verificationDocType("LABOR_CONTRACT")
                    .verificationDocUrls(List.of("https://cdn.pawnmaster.vn/hdld_new.jpg"))
                    .build();

            BusinessException createEx = assertThrows(BusinessException.class,
                    () -> customerService.createCustomer(blockedCreate));
            assertEquals(ErrorCode.INVALID_VERIFICATION_DOCUMENT, createEx.getErrorCode());

            // 3b. Updating existing customer's non-KYC field (notes) succeeds while keeping historical LABOR_CONTRACT
            CustomerResponse updatedNotes = customerService.updateCustomer(existingCustomer.getId(),
                    CustomerUpdateRequest.builder()
                            .notes("Cập nhật số tham chiếu")
                            .build());
            assertEquals("LABOR_CONTRACT", updatedNotes.getVerificationDocType());

            // 3c. Attempting to switch another customer onto deactivated LABOR_CONTRACT must fail
            CustomerResponse secondCustomer = customerService.createCustomer(CustomerCreateRequest.builder()
                    .fullName("Trần Thị Đổi Nghề")
                    .identityNumber("079094005566")
                    .phoneNumber("0905556677")
                    .address("30 Pasteur, Quận 1")
                    .occupationGroup(CustomerOccupationGroup.FREELANCER)
                    .monthlyIncome(new BigDecimal("12000000"))
                    .verificationDocType("RESIDENCE_CONFIRMATION_CT07")
                    .verificationDocUrls(List.of("https://cdn.pawnmaster.vn/ct07.jpg"))
                    .build());

            BusinessException changeEx = assertThrows(BusinessException.class, () -> customerService.updateCustomer(
                    secondCustomer.getId(),
                    CustomerUpdateRequest.builder()
                            .occupationGroup(CustomerOccupationGroup.OFFICE_WORKER)
                            .verificationDocType("LABOR_CONTRACT")
                            .build()));
            assertEquals(ErrorCode.INVALID_VERIFICATION_DOCUMENT, changeEx.getErrorCode());

            // 4a. Historical verified customer can still originate contract via createContract()
            CollateralEntity col = CollateralEntity.builder()
                    .collateralCode("TS-HIS-01")
                    .customer(customerStore.get(existingCustomer.getId()))
                    .branch(sampleBranch)
                    .name("iPhone 16 Pro Max")
                    .category(CollateralCategory.PHONE)
                    .appraisedValue(new BigDecimal("28000000"))
                    .maxLtvPercent(new BigDecimal("0.92"))
                    .status(CollateralStatus.IN_VAULT)
                    .build();
            col.setId(500L);
            collateralStore.put(500L, col);

            ContractResponse createdContract = contractService.createContract(ContractCreateRequest.builder()
                    .customerId(existingCustomer.getId())
                    .collateralId(500L)
                    .branchId(1L)
                    .principalAmount(new BigDecimal("15000000"))
                    .interestType(InterestType.DAILY_PER_MILLION)
                    .interestRate(new BigDecimal("2000"))
                    .loanTermDays(30)
                    .paymentCycleDays(10)
                    .startDate(LocalDate.of(2026, 11, 1))
                    .disbursementMethod(DisbursementMethod.CASH)
                    .build(), 99L);
            assertNotNull(createdContract);

            // 4b. Historical verified customer can also originateFullContract while preserving LABOR_CONTRACT
            ContractResponse quickOrig = contractService.originateFullContract(QuickContractOriginationRequest.builder()
                    .branchId(1L)
                    .customerFullName("Phạm Văn Lịch Sử")
                    .identityNumber("079088001122")
                    .phoneNumber("0901112233")
                    .occupationGroup(CustomerOccupationGroup.OFFICE_WORKER)
                    .verificationDocType("LABOR_CONTRACT")
                    .collateralCategory(CollateralCategory.MOTORBIKE)
                    .collateralName("Honda Vision")
                    .appraisedValue(new BigDecimal("30000000"))
                    .principalAmount(new BigDecimal("20000000"))
                    .interestRate(new BigDecimal("2500"))
                    .disbursementMethod(DisbursementMethod.CASH)
                    .build(), 99L);
            assertNotNull(quickOrig);
            assertEquals("LABOR_CONTRACT", customerStore.get(existingCustomer.getId()).getVerificationDocType());
        }

        @Test
        @DisplayName("Setting isDefault=true clears prior default option on the same occupation group")
        void upsertPolicyIsDefault_SwitchesDefaultCleanly() {
            assertEquals("PAYSLIP_BANK_STATEMENT",
                    verificationDocumentPolicy.getDefaultDocumentCode(CustomerOccupationGroup.OFFICE_WORKER));

            verificationDocumentPolicy.upsertOccupationDocumentPolicy(UpsertOccupationDocPolicyRequest.builder()
                    .occupationGroup(CustomerOccupationGroup.OFFICE_WORKER)
                    .docCode("SOCIAL_INSURANCE_VSSID")
                    .isDefault(true)
                    .active(true)
                    .build());

            assertEquals("SOCIAL_INSURANCE_VSSID",
                    verificationDocumentPolicy.getDefaultDocumentCode(CustomerOccupationGroup.OFFICE_WORKER));

            OccupationDocumentPolicyEntity prior = policyStore.get("OFFICE_WORKER::PAYSLIP_BANK_STATEMENT");
            OccupationDocumentPolicyEntity nextDefault = policyStore.get("OFFICE_WORKER::SOCIAL_INSURANCE_VSSID");
            assertNotNull(prior);
            assertNotNull(nextDefault);
            assertFalse(prior.isDefaultOption());
            assertTrue(nextDefault.isDefaultOption());
        }

        private void wireStatefulDocTypeRepository() {
            lenient().when(docTypeRepository.count()).thenAnswer(inv -> (long) docTypeStore.size());
            lenient().when(docTypeRepository.save(any(VerificationDocumentTypeEntity.class))).thenAnswer(inv -> {
                VerificationDocumentTypeEntity e = inv.getArgument(0);
                if (e.getId() == null) {
                    e.setId(seq.incrementAndGet());
                }
                docTypeStore.put(e.getDocCode().toUpperCase(), e);
                return e;
            });
            lenient().when(docTypeRepository.saveAll(anyList())).thenAnswer(inv -> {
                List<VerificationDocumentTypeEntity> list = inv.getArgument(0);
                for (VerificationDocumentTypeEntity e : list) {
                    if (e.getId() == null) {
                        e.setId(seq.incrementAndGet());
                    }
                    docTypeStore.put(e.getDocCode().toUpperCase(), e);
                }
                return list;
            });
            lenient().when(docTypeRepository.findAllByDeletedFalseOrderByDisplayOrderAscIdAsc()).thenAnswer(inv ->
                    docTypeStore.values().stream()
                            .filter(e -> !e.isDeleted())
                            .sorted(Comparator.comparingInt(VerificationDocumentTypeEntity::getDisplayOrder)
                                    .thenComparing(e -> e.getId() != null ? e.getId() : 0L))
                            .toList());
            lenient().when(docTypeRepository.findByDocCodeIgnoreCase(anyString())).thenAnswer(inv -> {
                String code = inv.getArgument(0);
                return Optional.ofNullable(docTypeStore.get(code.trim().toUpperCase()));
            });
            lenient().when(docTypeRepository.findByDocCodeIgnoreCaseAndDeletedFalse(anyString())).thenAnswer(inv -> {
                String code = inv.getArgument(0);
                VerificationDocumentTypeEntity hit = docTypeStore.get(code.trim().toUpperCase());
                return (hit != null && !hit.isDeleted()) ? Optional.of(hit) : Optional.empty();
            });
        }

        private void wireStatefulPolicyRepository() {
            lenient().when(policyRepository.count()).thenAnswer(inv -> (long) policyStore.size());
            lenient().when(policyRepository.save(any(OccupationDocumentPolicyEntity.class))).thenAnswer(inv -> {
                OccupationDocumentPolicyEntity p = inv.getArgument(0);
                if (p.getId() == null) {
                    p.setId(seq.incrementAndGet());
                }
                policyStore.put(p.getOccupationGroup().name() + "::" + p.getDocCode().toUpperCase(), p);
                return p;
            });
            lenient().when(policyRepository.saveAll(anyList())).thenAnswer(inv -> {
                List<OccupationDocumentPolicyEntity> list = inv.getArgument(0);
                for (OccupationDocumentPolicyEntity p : list) {
                    if (p.getId() == null) {
                        p.setId(seq.incrementAndGet());
                    }
                    policyStore.put(p.getOccupationGroup().name() + "::" + p.getDocCode().toUpperCase(), p);
                }
                return list;
            });
            lenient().when(policyRepository.findByOccupationGroupAndDocCodeIgnoreCase(any(), anyString())).thenAnswer(inv -> {
                CustomerOccupationGroup g = inv.getArgument(0);
                String code = inv.getArgument(1);
                return Optional.ofNullable(policyStore.get(g.name() + "::" + code.trim().toUpperCase()));
            });
            lenient().when(policyRepository.findAllByOccupationGroupAndDeletedFalseOrderByDisplayOrderAscIdAsc(any()))
                    .thenAnswer(inv -> {
                        CustomerOccupationGroup g = inv.getArgument(0);
                        return policyStore.values().stream()
                                .filter(p -> !p.isDeleted() && p.getOccupationGroup() == g)
                                .sorted(Comparator.comparingInt(OccupationDocumentPolicyEntity::getDisplayOrder)
                                        .thenComparing(p -> p.getId() != null ? p.getId() : 0L))
                                .toList();
                    });
            lenient().when(policyRepository.findAllByOccupationGroupAndActiveTrueAndDeletedFalseOrderByDisplayOrderAscIdAsc(any()))
                    .thenAnswer(inv -> {
                        CustomerOccupationGroup g = inv.getArgument(0);
                        return policyStore.values().stream()
                                .filter(p -> !p.isDeleted() && p.isActive() && p.getOccupationGroup() == g)
                                .sorted(Comparator.comparingInt(OccupationDocumentPolicyEntity::getDisplayOrder)
                                        .thenComparing(p -> p.getId() != null ? p.getId() : 0L))
                                .toList();
                    });
        }

        private void seedDefaultDbCatalogAndMatrix() {
            for (VerificationDocumentType dt : VerificationDocumentType.values()) {
                VerificationDocumentTypeEntity entity = VerificationDocumentTypeEntity.builder()
                        .docCode(dt.name())
                        .docName(dt.getDescription())
                        .docCategory(dt.getDocCategory())
                        .description(dt.getDescription())
                        .displayOrder(dt.getDisplayOrder())
                        .active(true)
                        .build();
                entity.setId(seq.incrementAndGet());
                docTypeStore.put(dt.name(), entity);
            }
            for (CustomerOccupationGroup group : CustomerOccupationGroup.values()) {
                List<VerificationDocumentType> defaults = List.copyOf(
                        new VerificationDocumentPolicy().getAllowedDocuments(group));
                for (int i = 0; i < defaults.size(); i++) {
                    VerificationDocumentType dt = defaults.get(i);
                    OccupationDocumentPolicyEntity p = OccupationDocumentPolicyEntity.builder()
                            .occupationGroup(group)
                            .docCode(dt.name())
                            .defaultOption(i == 0)
                            .displayOrder(i + 1)
                            .active(true)
                            .build();
                    p.setId(seq.incrementAndGet());
                    policyStore.put(group.name() + "::" + dt.name(), p);
                }
            }
        }
    }

    @Nested
    @WebMvcTest(controllers = CustomerController.class)
    @Import({SecurityConfig.class, VerificationDocumentPolicy.class, JwtAuthenticationFilter.class, GlobalExceptionHandler.class})
    @DisplayName("2. Security & MVC Endpoint Contract Tests (/api/v1/customers/verification-doc-*)")
    class SecurityAndMvcControllerTests {

        @Autowired
        private MockMvc mockMvc;

        @Autowired
        private ObjectMapper objectMapper;

        @MockBean
        private CustomerService customerService;

        @MockBean
        private VerificationDocumentTypeRepository docTypeRepository;

        @MockBean
        private OccupationDocumentPolicyRepository policyRepository;

        @MockBean
        private JwtTokenProvider jwtTokenProvider;

        @MockBean
        private UserDetailsServiceImpl userDetailsService;

        @MockBean
        private JpaMetamodelMappingContext jpaMetamodelMappingContext;

        @BeforeEach
        void setUpMocks() {
            VerificationDocumentTypeEntity taxEntity = VerificationDocumentTypeEntity.builder()
                    .docCode("PAYSLIP_BANK_STATEMENT")
                    .docName("Bảng lương / Sao kê ngân hàng 3 tháng gần nhất")
                    .docCategory("INCOME")
                    .displayOrder(1)
                    .active(true)
                    .build();
            taxEntity.setId(1L);

            OccupationDocumentPolicyEntity policyEntity = OccupationDocumentPolicyEntity.builder()
                    .occupationGroup(CustomerOccupationGroup.OFFICE_WORKER)
                    .docCode("PAYSLIP_BANK_STATEMENT")
                    .defaultOption(true)
                    .displayOrder(1)
                    .active(true)
                    .build();
            policyEntity.setId(10L);

            lenient().when(docTypeRepository.count()).thenReturn(1L);
            lenient().when(policyRepository.count()).thenReturn(1L);
            lenient().when(docTypeRepository.findAllByDeletedFalseOrderByDisplayOrderAscIdAsc())
                    .thenReturn(List.of(taxEntity));
            lenient().when(policyRepository.findAllByOccupationGroupAndActiveTrueAndDeletedFalseOrderByDisplayOrderAscIdAsc(
                    CustomerOccupationGroup.OFFICE_WORKER))
                    .thenReturn(List.of(policyEntity));
            lenient().when(policyRepository.findAllByOccupationGroupAndDeletedFalseOrderByDisplayOrderAscIdAsc(
                    CustomerOccupationGroup.OFFICE_WORKER))
                    .thenReturn(List.of(policyEntity));
        }

        @Test
        @DisplayName("Public GET /verification-doc-types and GET /verification-doc-policies return 200 without auth")
        void publicGetEndpoints_Return200WithoutAuth() throws Exception {
            mockMvc.perform(get("/api/v1/customers/verification-doc-types")
                            .param("occupationGroup", "OFFICE_WORKER"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data[0].code").value("PAYSLIP_BANK_STATEMENT"))
                    .andExpect(jsonPath("$.data[0].defaultForGroup").value(true));

            mockMvc.perform(get("/api/v1/customers/verification-doc-policies"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.policiesByOccupation.OFFICE_WORKER[0].docCode").value("PAYSLIP_BANK_STATEMENT"))
                    .andExpect(jsonPath("$.data.policiesByOccupation.OFFICE_WORKER[0].isDefault").value(true));
        }

        @Test
        @DisplayName("Unauthenticated POST/PUT config endpoints return 401 JSON")
        void unauthenticatedAdminEndpoints_Return401() throws Exception {
            UpsertVerificationDocTypeRequest typeReq = UpsertVerificationDocTypeRequest.builder()
                    .code("TAX_RETURN_PIT")
                    .name("Tờ khai quyết toán thuế TNCN")
                    .docCategory("INCOME")
                    .build();

            mockMvc.perform(post("/api/v1/customers/verification-doc-types")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(typeReq)))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.success").value(false));

            UpsertOccupationDocPolicyRequest policyReq = UpsertOccupationDocPolicyRequest.builder()
                    .occupationGroup(CustomerOccupationGroup.OFFICE_WORKER)
                    .docCode("PAYSLIP_BANK_STATEMENT")
                    .isDefault(true)
                    .build();

            mockMvc.perform(put("/api/v1/customers/verification-doc-policies")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(policyReq)))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.success").value(false));
        }

        @Test
        @WithMockUser(roles = "STAFF")
        @DisplayName("STAFF role receives 403 Forbidden on POST/PUT config endpoints")
        void staffRoleAdminEndpoints_Return403() throws Exception {
            UpsertVerificationDocTypeRequest typeReq = UpsertVerificationDocTypeRequest.builder()
                    .code("TAX_RETURN_PIT")
                    .name("Tờ khai quyết toán thuế TNCN")
                    .docCategory("INCOME")
                    .build();

            mockMvc.perform(post("/api/v1/customers/verification-doc-types")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(typeReq)))
                    .andExpect(status().isForbidden());

            UpsertOccupationDocPolicyRequest policyReq = UpsertOccupationDocPolicyRequest.builder()
                    .occupationGroup(CustomerOccupationGroup.OFFICE_WORKER)
                    .docCode("PAYSLIP_BANK_STATEMENT")
                    .isDefault(true)
                    .build();

            mockMvc.perform(put("/api/v1/customers/verification-doc-policies")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(policyReq)))
                    .andExpect(status().isForbidden());
        }

        @Test
        @WithMockUser(roles = "ADMIN")
        @DisplayName("ADMIN role succeeds with 200 OK on POST/PUT config endpoints")
        void adminRoleAdminEndpoints_Return200() throws Exception {
            VerificationDocumentTypeEntity savedType = VerificationDocumentTypeEntity.builder()
                    .docCode("TAX_RETURN_PIT")
                    .docName("Tờ khai quyết toán thuế TNCN")
                    .docCategory("INCOME")
                    .displayOrder(20)
                    .active(true)
                    .build();
            savedType.setId(20L);

            lenient().when(docTypeRepository.findByDocCodeIgnoreCase("TAX_RETURN_PIT")).thenReturn(Optional.empty());
            lenient().when(docTypeRepository.findByDocCodeIgnoreCaseAndDeletedFalse("TAX_RETURN_PIT"))
                    .thenReturn(Optional.of(savedType));
            lenient().when(docTypeRepository.save(any(VerificationDocumentTypeEntity.class))).thenReturn(savedType);

            UpsertVerificationDocTypeRequest typeReq = UpsertVerificationDocTypeRequest.builder()
                    .code("tax_return_pit")
                    .name("Tờ khai quyết toán thuế TNCN")
                    .docCategory("INCOME")
                    .displayOrder(20)
                    .active(true)
                    .build();

            mockMvc.perform(post("/api/v1/customers/verification-doc-types")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(typeReq)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.code").value("TAX_RETURN_PIT"));

            OccupationDocumentPolicyEntity savedPolicy = OccupationDocumentPolicyEntity.builder()
                    .occupationGroup(CustomerOccupationGroup.OFFICE_WORKER)
                    .docCode("TAX_RETURN_PIT")
                    .defaultOption(true)
                    .displayOrder(4)
                    .active(true)
                    .build();
            savedPolicy.setId(99L);

            lenient().when(policyRepository.findByOccupationGroupAndDocCodeIgnoreCase(
                    CustomerOccupationGroup.OFFICE_WORKER, "TAX_RETURN_PIT")).thenReturn(Optional.empty());
            lenient().when(policyRepository.save(any(OccupationDocumentPolicyEntity.class))).thenReturn(savedPolicy);

            UpsertOccupationDocPolicyRequest policyReq = UpsertOccupationDocPolicyRequest.builder()
                    .occupationGroup(CustomerOccupationGroup.OFFICE_WORKER)
                    .docCode("TAX_RETURN_PIT")
                    .isDefault(true)
                    .displayOrder(4)
                    .active(true)
                    .build();

            mockMvc.perform(put("/api/v1/customers/verification-doc-policies")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(policyReq)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.docCode").value("TAX_RETURN_PIT"))
                    .andExpect(jsonPath("$.data.isDefault").value(true));
        }
    }
}
