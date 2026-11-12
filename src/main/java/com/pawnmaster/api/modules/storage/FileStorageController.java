package com.pawnmaster.api.modules.storage;

import com.pawnmaster.api.common.BaseResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/v1/files")
@RequiredArgsConstructor
public class FileStorageController {

    private final FileStorageService fileStorageService;

    @PostMapping(value = "/customer-verification-docs", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public BaseResponse<FileStorageService.VerificationFolderResult> uploadCustomerVerificationDocs(
            @RequestParam("identityNumber") String identityNumber,
            @RequestParam(value = "files", required = false) List<MultipartFile> files,
            @RequestParam(value = "keepUrls", required = false) List<String> keepUrls,
            @RequestParam(value = "syncExistingFolder", defaultValue = "false") boolean syncExistingFolder
    ) {
        FileStorageService.VerificationFolderResult result =
                fileStorageService.storeCustomerVerificationDocs(identityNumber, files, keepUrls, syncExistingFolder);
        return BaseResponse.ok("Đã lưu hình ảnh chứng từ xác minh vào thư mục riêng", result);
    }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public BaseResponse<FileStorageService.FileBatchUploadResult> uploadCategorizedImages(
            @RequestParam("category") String category,
            @RequestParam(value = "ownerKey", required = false) String ownerKey,
            @RequestParam("files") List<MultipartFile> files
    ) {
        FileStorageService.FileBatchUploadResult result =
                fileStorageService.storeCategorizedImages(category, ownerKey, files);
        return BaseResponse.ok("Tải hình ảnh lên thành công", result);
    }

    @GetMapping("/folder-images")
    public BaseResponse<List<String>> listFolderImages(@RequestParam("folder") String folder) {
        return BaseResponse.ok(fileStorageService.listFolderImages(folder));
    }
}
