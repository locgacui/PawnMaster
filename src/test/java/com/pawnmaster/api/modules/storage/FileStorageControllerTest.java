package com.pawnmaster.api.modules.storage;

import com.pawnmaster.api.common.BusinessException;
import com.pawnmaster.api.modules.customer.CustomerEntity;
import com.pawnmaster.api.modules.customer.CustomerOccupationGroup;
import com.pawnmaster.api.modules.customer.CustomerRepository;
import com.pawnmaster.api.modules.customer.CustomerServiceImpl;
import com.pawnmaster.api.modules.customer.CustomerStatus;
import com.pawnmaster.api.modules.customer.VerificationDocumentPolicy;
import com.pawnmaster.api.modules.customer.VerificationDocumentType;
import com.pawnmaster.api.modules.customer.dto.CustomerResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class FileStorageControllerTest {

    @TempDir
    Path tempUploadDir;

    private LocalFileStorageService storageService;
    private MockMvc mockMvc;

    // Valid minimal JPEG & PNG headers
    private static final byte[] SAMPLE_JPEG_BYTES = new byte[]{
            (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0,
            0x00, 0x10, 0x4A, 0x46, 0x49, 0x46, 0x00, 0x01,
            (byte) 0xFF, (byte) 0xD9
    };

    private static final byte[] SAMPLE_PNG_BYTES = new byte[]{
            (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
            0x00, 0x00, 0x00, 0x0D, 0x49, 0x48, 0x44, 0x52
    };

    @BeforeEach
    void setUp() {
        storageService = new LocalFileStorageService(tempUploadDir.toString());
        FileStorageController controller = new FileStorageController(storageService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new com.pawnmaster.api.common.GlobalExceptionHandler())
                .build();
    }

    @Test
    void uploadVerificationDocs_CreatesDedicatedCustomerFolderAndListsImages() throws Exception {
        MockMultipartFile doc1 = new MockMultipartFile(
                "files", "hop-dong-lao-dong.jpg", MediaType.IMAGE_JPEG_VALUE, SAMPLE_JPEG_BYTES);
        MockMultipartFile doc2 = new MockMultipartFile(
                "files", "sao-ke-luong.png", MediaType.IMAGE_PNG_VALUE, SAMPLE_PNG_BYTES);

        mockMvc.perform(multipart("/api/v1/files/customer-verification-docs")
                        .file(doc1)
                        .file(doc2)
                        .param("identityNumber", "079201004821"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.folderUrl").value("/uploads/customers/079201004821/verification-docs"))
                .andExpect(jsonPath("$.data.totalFiles").value(2))
                .andExpect(jsonPath("$.data.imageUrls.length()").value(2));

        Path expectedFolder = tempUploadDir.resolve("customers/079201004821/verification-docs");
        assertThat(Files.isDirectory(expectedFolder)).isTrue();

        mockMvc.perform(get("/api/v1/files/folder-images")
                        .param("folder", "/uploads/customers/079201004821/verification-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.length()").value(2));
    }

    @Test
    void customerService_ExpandsFolderUrlIntoVerificationDocUrlListWithoutChangingFolderField() {
        MockMultipartFile doc1 = new MockMultipartFile(
                "files", "ct07-page1.jpg", MediaType.IMAGE_JPEG_VALUE, SAMPLE_JPEG_BYTES);
        MockMultipartFile doc2 = new MockMultipartFile(
                "files", "ct07-page2.png", MediaType.IMAGE_PNG_VALUE, SAMPLE_PNG_BYTES);

        LocalFileStorageService.VerificationFolderResult stored =
                storageService.storeCustomerVerificationDocs("079198005432", List.of(doc1, doc2), null, false);

        CustomerRepository repo = mock(CustomerRepository.class);
        CustomerEntity existing = CustomerEntity.builder()
                .customerCode("KH-2611-TEST01")
                .fullName("Trần Thị Mai")
                .identityNumber("079198005432")
                .phoneNumber("0913456789")
                .occupationGroup(CustomerOccupationGroup.SMALL_MERCHANT)
                .verificationDocType(VerificationDocumentType.BUSINESS_LICENSE_OR_STALL_LEASE)
                .verificationDocUrls(stored.folderUrl())
                .status(CustomerStatus.ACTIVE)
                .build();
        existing.setId(10L);
        when(repo.findByIdentityNumberAndDeletedFalse("079198005432")).thenReturn(Optional.of(existing));

        CustomerServiceImpl customerService = new CustomerServiceImpl(repo, new VerificationDocumentPolicy());
        customerService.setFileStorageService(storageService);

        CustomerResponse response = customerService.getCustomerByIdentityNumber("079198005432");
        assertThat(response.getVerificationDocUrls()).isEqualTo("/uploads/customers/079198005432/verification-docs");
        assertThat(response.getVerificationDocUrlList()).hasSize(2);
        assertThat(response.getVerificationDocUrlList().get(0))
                .startsWith("/uploads/customers/079198005432/verification-docs/");
    }

    @Test
    void customerService_PreservesLegacyCommaSeparatedUrlsWhenNotAFolder() {
        List<String> resolved = storageService.resolveImageUrlsFromStoredValue(
                "/uploads/docs/kh_002_contract.jpg,/uploads/docs/kh_002_payslip.jpg");
        assertThat(resolved).containsExactly(
                "/uploads/docs/kh_002_contract.jpg",
                "/uploads/docs/kh_002_payslip.jpg"
        );
    }

    @Test
    void uploadSingleKycAndRejectInvalidMagicBytesOrPathTraversal() throws Exception {
        MockMultipartFile fakeImage = new MockMultipartFile(
                "files", "malware.jpg", MediaType.IMAGE_JPEG_VALUE,
                "not-an-image-payload".getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(multipart("/api/v1/files/upload")
                        .file(fakeImage)
                        .param("category", "CCCD_FRONT")
                        .param("ownerKey", "079201004821"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));

        assertThatThrownBy(() -> storageService.listFolderImages("/uploads/../../Windows/System32"))
                .isInstanceOf(BusinessException.class);
    }
}
