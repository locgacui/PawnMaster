package com.pawnmaster.api.modules.customer;

import com.pawnmaster.api.common.BusinessException;
import com.pawnmaster.api.common.ErrorCode;
import com.pawnmaster.api.modules.branch.BranchEntity;
import com.pawnmaster.api.modules.branch.BranchRepository;
import com.pawnmaster.api.modules.customer.dto.CustomerCreateRequest;
import com.pawnmaster.api.modules.customer.dto.CustomerResponse;
import com.pawnmaster.api.modules.customer.dto.CustomerUpdateRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Checkpoint 2: CustomerService Unit Tests")
public class CustomerServiceTest {

    @Mock
    private CustomerRepository customerRepository;

    @Mock
    private BranchRepository branchRepository;

    @Spy
    private VerificationDocumentPolicy verificationDocumentPolicy = new VerificationDocumentPolicy();

    @InjectMocks
    private CustomerServiceImpl customerService;

    private CustomerEntity sampleCustomer;
    private CustomerCreateRequest createRequest;
    private BranchEntity sampleBranch;

    @BeforeEach
    void setUp() {
        sampleBranch = BranchEntity.builder()
                .branchCode("CN-HCM-01")
                .branchName("Chi nhánh Quận 1")
                .provinceCode("79")
                .districtCode("760")
                .build();
        sampleBranch.setId(1L);

        sampleCustomer = CustomerEntity.builder()
                .customerCode("KH-0001")
                .fullName("Nguyễn Văn A")
                .identityNumber("079090123456")
                .identityIssueDate(LocalDate.of(2021, 5, 10))
                .identityIssuePlace("Cục CSQLHC về TTXH")
                .phoneNumber("0901234567")
                .email("nguyenvana@gmail.com")
                .dateOfBirth(LocalDate.of(1990, 1, 15))
                .gender("Nam")
                .address("123 Lê Lợi, Bến Nghé, Quận 1")
                .permanentAddress("123 Lê Lợi, Bến Nghé, Quận 1")
                .occupationGroup(CustomerOccupationGroup.OFFICE_WORKER)
                .monthlyIncome(new BigDecimal("15000000.00"))
                .hasSocialInsurance(true)
                .verificationDocType(VerificationDocumentType.PAYSLIP_BANK_STATEMENT)
                .verificationDocUrls("https://s3.aws.com/pawn/docs/slip1.png,https://s3.aws.com/pawn/docs/slip2.png")
                .status(CustomerStatus.ACTIVE)
                .notes("Khách hàng văn phòng uy tín")
                .build();
        sampleCustomer.setId(100L);

        createRequest = CustomerCreateRequest.builder()
                .fullName("Nguyễn Văn A")
                .identityNumber("079090123456")
                .identityIssueDate(LocalDate.of(2021, 5, 10))
                .identityIssuePlace("Cục CSQLHC về TTXH")
                .phoneNumber("0901234567")
                .email("nguyenvana@gmail.com")
                .dateOfBirth(LocalDate.of(1990, 1, 15))
                .gender("Nam")
                .provinceCode("79")
                .districtCode("760")
                .address("123 Lê Lợi, Bến Nghé, Quận 1")
                .permanentAddress("123 Lê Lợi, Bến Nghé, Quận 1")
                .occupationGroup(CustomerOccupationGroup.OFFICE_WORKER)
                .monthlyIncome(new BigDecimal("15000000.00"))
                .hasSocialInsurance(true)
                .verificationDocType(VerificationDocumentType.PAYSLIP_BANK_STATEMENT)
                .verificationDocUrls(
                        List.of("https://s3.aws.com/pawn/docs/slip1.png", "https://s3.aws.com/pawn/docs/slip2.png"))
                .branchId(1L)
                .notes("Khách hàng văn phòng uy tín")
                .build();
    }

    // =========================================================================
    // 1. CUSTOMER CREATION TESTS
    // =========================================================================

    @Test
    @DisplayName("Create customer: Success with Office Worker and Payslip verification document")
    void createCustomer_Success() {
        when(customerRepository.existsByIdentityNumber("079090123456")).thenReturn(false);
        when(customerRepository.save(any(CustomerEntity.class))).thenAnswer(invocation -> {
            CustomerEntity entity = invocation.getArgument(0);
            entity.setId(100L);
            return entity;
        });

        CustomerResponse response = customerService.createCustomer(createRequest);

        assertNotNull(response);
        assertEquals("079090123456", response.getIdentityNumber());
        assertEquals("Nguyễn Văn A", response.getFullName());
        assertEquals(CustomerOccupationGroup.OFFICE_WORKER, response.getOccupationGroup());
        assertEquals(VerificationDocumentType.PAYSLIP_BANK_STATEMENT, response.getVerificationDocType());
        assertTrue(response.isHasSocialInsurance());
        assertEquals(0, new BigDecimal("15000000.00").compareTo(response.getMonthlyIncome()));

        verify(customerRepository, times(1)).save(any(CustomerEntity.class));
    }

    @Test
    @DisplayName("Create customer: Duplicate CCCD/CMND throws CUSTOMER_IDENTITY_EXISTS")
    void createCustomer_DuplicateIdentityNumber_ThrowsCustomerIdentityExists() {
        when(customerRepository.existsByIdentityNumber("079090123456")).thenReturn(true);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> customerService.createCustomer(createRequest));

        assertEquals(ErrorCode.CUSTOMER_IDENTITY_EXISTS, ex.getErrorCode());
        verify(customerRepository, never()).save(any());
    }

    @Test
    @DisplayName("Create customer: Mismatched verification doc throws INVALID_VERIFICATION_DOCUMENT")
    void createCustomer_MismatchedVerificationDoc_ThrowsInvalidVerificationDocument() {
        createRequest.setOccupationGroup(CustomerOccupationGroup.FREELANCER);
        createRequest.setVerificationDocType(VerificationDocumentType.PAYSLIP_BANK_STATEMENT); // Invalid for
                                                                                               // freelancer!
        when(customerRepository.existsByIdentityNumber("079090123456")).thenReturn(false);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> customerService.createCustomer(createRequest));

        assertEquals(ErrorCode.INVALID_VERIFICATION_DOCUMENT, ex.getErrorCode());
        verify(customerRepository, never()).save(any());
    }

    @Test
    @DisplayName("Create customer: Missing verification doc URLs throws MISSING_VERIFICATION_DOCUMENTS")
    void createCustomer_MissingDocUrls_ThrowsMissingVerificationDocuments() {
        createRequest.setVerificationDocUrls(Collections.emptyList());
        when(customerRepository.existsByIdentityNumber("079090123456")).thenReturn(false);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> customerService.createCustomer(createRequest));

        assertEquals(ErrorCode.MISSING_VERIFICATION_DOCUMENTS, ex.getErrorCode());
        verify(customerRepository, never()).save(any());
    }

    @Test
    @DisplayName("Create customer: Blank verification doc URLs throws MISSING_VERIFICATION_DOCUMENTS")
    void createCustomer_BlankDocUrls_ThrowsMissingVerificationDocuments() {
        createRequest.setVerificationDocUrls(List.of("   ", ""));
        when(customerRepository.existsByIdentityNumber("079090123456")).thenReturn(false);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> customerService.createCustomer(createRequest));

        assertEquals(ErrorCode.MISSING_VERIFICATION_DOCUMENTS, ex.getErrorCode());
        verify(customerRepository, never()).save(any());
    }

    // =========================================================================
    // 2. CUSTOMER RETRIEVAL & LOOKUP TESTS
    // =========================================================================

    @Test
    @DisplayName("Get customer by ID: Success")
    void getCustomerById_Success() {
        when(customerRepository.findById(100L)).thenReturn(Optional.of(sampleCustomer));

        CustomerResponse response = customerService.getCustomerById(100L);

        assertNotNull(response);
        assertEquals(100L, response.getId());
        assertEquals("KH-0001", response.getCustomerCode());
        assertEquals("079090123456", response.getIdentityNumber());
    }

    @Test
    @DisplayName("Get customer by ID: Not found throws CUSTOMER_NOT_FOUND")
    void getCustomerById_NotFound_ThrowsCustomerNotFound() {
        when(customerRepository.findById(999L)).thenReturn(Optional.empty());

        BusinessException ex = assertThrows(BusinessException.class, () -> customerService.getCustomerById(999L));

        assertEquals(ErrorCode.CUSTOMER_NOT_FOUND, ex.getErrorCode());
    }

    @Test
    @DisplayName("Get customer by CCCD/CMND: Success")
    void getCustomerByIdentityNumber_Success() {
        when(customerRepository.findByIdentityNumberAndDeletedFalse("079090123456"))
                .thenReturn(Optional.of(sampleCustomer));

        CustomerResponse response = customerService.getCustomerByIdentityNumber("079090123456");

        assertNotNull(response);
        assertEquals("079090123456", response.getIdentityNumber());
        assertEquals("Nguyễn Văn A", response.getFullName());
    }

    @Test
    @DisplayName("Get customer by CCCD/CMND: Not found throws CUSTOMER_NOT_FOUND")
    void getCustomerByIdentityNumber_NotFound_ThrowsCustomerNotFound() {
        when(customerRepository.findByIdentityNumberAndDeletedFalse("999999999999"))
                .thenReturn(Optional.empty());

        BusinessException ex = assertThrows(BusinessException.class,
                () -> customerService.getCustomerByIdentityNumber("999999999999"));

        assertEquals(ErrorCode.CUSTOMER_NOT_FOUND, ex.getErrorCode());
    }

    // =========================================================================
    // 3. CUSTOMER UPDATE & SOFT DELETE TESTS
    // =========================================================================

    @Test
    @DisplayName("Update customer: Success with updated income and notes")
    void updateCustomer_Success() {
        when(customerRepository.findById(100L)).thenReturn(Optional.of(sampleCustomer));

        CustomerUpdateRequest updateRequest = CustomerUpdateRequest.builder()
                .fullName("Nguyễn Văn A (Updated)")
                .phoneNumber("0909999888")
                .monthlyIncome(new BigDecimal("20000000.00"))
                .notes("Tăng hạn mức thu nhập")
                .build();

        CustomerResponse response = customerService.updateCustomer(100L, updateRequest);

        assertNotNull(response);
        assertEquals("Nguyễn Văn A (Updated)", response.getFullName());
        assertEquals("0909999888", response.getPhoneNumber());
        assertEquals(0, new BigDecimal("20000000.00").compareTo(response.getMonthlyIncome()));
    }

    @Test
    @DisplayName("Delete customer: Soft delete sets isDeleted true")
    void deleteCustomer_SoftDelete_Success() {
        when(customerRepository.findById(100L)).thenReturn(Optional.of(sampleCustomer));

        customerService.deleteCustomer(100L);

        assertTrue(sampleCustomer.isDeleted());
    }
}
