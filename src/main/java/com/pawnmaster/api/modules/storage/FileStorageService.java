package com.pawnmaster.api.modules.storage;

import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * Storage port abstraction for customer KYC documents and collateral inspection photos.
 * Keeps domain services decoupled from underlying storage adapters (local disk vs S3/MinIO).
 */
public interface FileStorageService {

    record VerificationFolderResult(
            String folderUrl,
            List<String> imageUrls,
            int totalFiles
    ) {
        public VerificationFolderResult {
            imageUrls = imageUrls != null ? List.copyOf(imageUrls) : List.of();
        }
    }

    record FileBatchUploadResult(
            String primaryUrl,
            List<String> imageUrls,
            int totalFiles
    ) {
        public FileBatchUploadResult {
            imageUrls = imageUrls != null ? List.copyOf(imageUrls) : List.of();
        }
    }

    VerificationFolderResult storeCustomerVerificationDocs(
            String identityNumber,
            List<MultipartFile> files,
            List<String> keepUrls,
            boolean syncExistingFolder
    );

    FileBatchUploadResult storeCategorizedImages(
            String category,
            String ownerKey,
            List<MultipartFile> files
    );

    List<String> listFolderImages(String folderUrl);

    List<String> resolveImageUrlsFromStoredValue(String storedValue);
}

