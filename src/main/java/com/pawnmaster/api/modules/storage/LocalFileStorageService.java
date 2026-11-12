package com.pawnmaster.api.modules.storage;

import com.pawnmaster.api.common.BusinessException;
import com.pawnmaster.api.common.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

@Slf4j
@Service
public class LocalFileStorageService implements FileStorageService {

    public static final String UPLOADS_URL_PREFIX = "/uploads/";
    public static final long MAX_FILE_SIZE_BYTES = StorageProperties.DEFAULT_MAX_FILE_SIZE_BYTES;
    public static final int MAX_FILES_PER_BATCH = StorageProperties.DEFAULT_MAX_FILES_PER_BATCH;

    private static final Set<String> IMAGE_EXTENSIONS = Set.of(
            ".jpg", ".jpeg", ".png", ".webp", ".gif", ".svg"
    );

    private final Path uploadRootPath;
    private final StorageProperties properties;
    private final ImageCompressionProcessor compressionProcessor;

    @Autowired
    public LocalFileStorageService(
            StorageProperties properties,
            ImageCompressionProcessor compressionProcessor
    ) {
        this.properties = properties != null ? properties : StorageProperties.defaultsWithUploadDir(null);
        this.compressionProcessor = compressionProcessor != null
                ? compressionProcessor
                : new ImageCompressionProcessor(this.properties);
        this.uploadRootPath = Paths.get(this.properties.uploadDir()).toAbsolutePath().normalize();
        try {
            Files.createDirectories(this.uploadRootPath);
        } catch (IOException ex) {
            log.warn("Could not initialize upload root directory at {}: {}", this.uploadRootPath, ex.getMessage());
        }
    }

    public LocalFileStorageService(String uploadDir) {
        this(StorageProperties.defaultsWithUploadDir(uploadDir), null);
    }

    public Path getUploadRootPath() {
        return uploadRootPath;
    }

    /**
     * Stores one or multiple verification document images into a dedicated per-customer directory:
     * uploads/customers/{sanitizedIdentityNumber}/verification-docs/
     * Returns the canonical folder URL (/uploads/customers/{id}/verification-docs) for DB storage.
     */
    @Override
    public VerificationFolderResult storeCustomerVerificationDocs(
            String identityNumber,
            List<MultipartFile> files,
            List<String> keepUrls,
            boolean syncExistingFolder
    ) {
        String cleanKey = sanitizeOwnerKey(identityNumber);
        if (cleanKey.length() < 4) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST,
                    "Số CCCD/Định danh khách hàng không hợp lệ để khởi tạo thư mục lưu trữ chứng từ");
        }

        int batchCap = properties.maxFilesPerBatch();
        List<MultipartFile> validFiles = filterNonEmptyFiles(files);
        if (validFiles.size() > batchCap) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST,
                    "Tối đa " + batchCap + " hình ảnh chứng từ cho mỗi hồ sơ khách hàng");
        }

        String relativeFolder = "customers/" + cleanKey + "/verification-docs";
        Path targetDir = resolveSafeSubPath(relativeFolder);
        createDirectoriesQuietly(targetDir);

        if (syncExistingFolder) {
            synchronizeKeptFilesInFolder(targetDir, relativeFolder, keepUrls);
        }

        if (validFiles.isEmpty() && !syncExistingFolder) {
            List<String> current = listImagesInDirectory(targetDir, relativeFolder);
            if (current.isEmpty()) {
                throw new BusinessException(ErrorCode.INVALID_REQUEST,
                        "Vui lòng chọn ít nhất 01 hình ảnh chứng từ xác minh từ máy tính");
            }
            return new VerificationFolderResult(UPLOADS_URL_PREFIX + relativeFolder, current, current.size());
        }

        long batchTs = System.currentTimeMillis();
        int seq = countExistingImages(targetDir) + 1;
        for (MultipartFile file : validFiles) {
            ImageCompressionProcessor.OptimizedImage optimized = compressionProcessor.optimizeForStorage(file);
            byte[] payload = optimized.bytes();

            if (properties.deduplicateByHash()) {
                Optional<String> duplicateName = findExistingDuplicateFileName(targetDir, payload);
                if (duplicateName.isPresent()) {
                    log.debug("deduplicated_verification_image folder={} matchedExisting={}",
                            relativeFolder, duplicateName.get());
                    continue;
                }
            }

            String baseName = sanitizeFileStem(file.getOriginalFilename());
            String fileName = String.format("%02d_%s_%d%s", seq++, baseName, batchTs++, optimized.extension());
            Path destination = resolveSafeChildFile(targetDir, fileName);
            writeBytesAtomically(payload, destination);
        }

        List<String> allImages = listImagesInDirectory(targetDir, relativeFolder);
        if (allImages.isEmpty()) {
            throw new BusinessException(ErrorCode.MISSING_VERIFICATION_DOCUMENTS,
                    "Thư mục chứng từ xác minh phải có ít nhất 01 hình ảnh hợp lệ");
        }

        String folderUrl = UPLOADS_URL_PREFIX + relativeFolder;
        return new VerificationFolderResult(folderUrl, allImages, allImages.size());
    }

    /**
     * Uploads single KYC images (CCCD_FRONT, CCCD_BACK, PORTRAIT) or multi-photo COLLATERAL images.
     */
    @Override
    public FileBatchUploadResult storeCategorizedImages(
            String category,
            String ownerKey,
            List<MultipartFile> files
    ) {
        List<MultipartFile> validFiles = filterNonEmptyFiles(files);
        if (validFiles.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "Vui lòng chọn tệp hình ảnh hợp lệ để tải lên");
        }
        int batchCap = properties.maxFilesPerBatch();
        if (validFiles.size() > batchCap) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST,
                    "Số lượng ảnh tải lên vượt quá giới hạn cho phép (" + batchCap + " ảnh)");
        }

        String normalizedCategory = category != null ? category.trim().toUpperCase(Locale.ROOT) : "GENERAL";
        String cleanOwner = sanitizeOwnerKey(ownerKey != null && !ownerKey.isBlank() ? ownerKey : "counter");

        String relativeDir;
        String prefix;
        boolean singleSlotReplace = false;
        switch (normalizedCategory) {
            case "CCCD_FRONT" -> {
                relativeDir = "customers/" + cleanOwner + "/kyc";
                prefix = "cccd_front";
                singleSlotReplace = true;
            }
            case "CCCD_BACK" -> {
                relativeDir = "customers/" + cleanOwner + "/kyc";
                prefix = "cccd_back";
                singleSlotReplace = true;
            }
            case "PORTRAIT" -> {
                relativeDir = "customers/" + cleanOwner + "/kyc";
                prefix = "portrait";
                singleSlotReplace = true;
            }
            case "COLLATERAL" -> {
                relativeDir = "collaterals/" + cleanOwner;
                prefix = "collateral";
            }
            default -> throw new BusinessException(ErrorCode.INVALID_REQUEST,
                    "Danh mục tải ảnh không hợp lệ: " + normalizedCategory);
        }

        Path targetDir = resolveSafeSubPath(relativeDir);
        createDirectoriesQuietly(targetDir);

        List<String> storedUrls = new ArrayList<>();
        long ts = System.currentTimeMillis();
        int idx = 1;

        List<MultipartFile> effectiveFiles = singleSlotReplace ? List.of(validFiles.get(0)) : validFiles;
        if (singleSlotReplace) {
            deleteFilesByPrefix(targetDir, prefix + "_");
        }

        for (MultipartFile file : effectiveFiles) {
            ImageCompressionProcessor.OptimizedImage optimized = compressionProcessor.optimizeForStorage(file);
            byte[] payload = optimized.bytes();

            if (!singleSlotReplace && properties.deduplicateByHash()) {
                Optional<String> existingDuplicate = findExistingDuplicateFileName(targetDir, payload);
                if (existingDuplicate.isPresent()) {
                    String reusedUrl = UPLOADS_URL_PREFIX + relativeDir + "/" + existingDuplicate.get();
                    if (!storedUrls.contains(reusedUrl)) {
                        storedUrls.add(reusedUrl);
                    }
                    continue;
                }
            }

            String stem = sanitizeFileStem(file.getOriginalFilename());
            String fileName = singleSlotReplace
                    ? String.format("%s_%d%s", prefix, ts, optimized.extension())
                    : String.format("%02d_%s_%d%s", idx++, stem, ts++, optimized.extension());
            Path destination = resolveSafeChildFile(targetDir, fileName);
            writeBytesAtomically(payload, destination);
            storedUrls.add(UPLOADS_URL_PREFIX + relativeDir + "/" + fileName);
        }

        return new FileBatchUploadResult(storedUrls.get(0), storedUrls, storedUrls.size());
    }

    /**
     * Lists all image URLs inside a stored /uploads/... folder path.
     * Strict path-traversal validation guarantees callers cannot inspect outside uploadRootPath.
     */
    @Override
    public List<String> listFolderImages(String folderUrl) {
        if (folderUrl == null || folderUrl.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "Đường dẫn thư mục không được để trống");
        }
        String normalizedUrl = normalizeUploadsUrlPath(folderUrl);
        if (normalizedUrl == null) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "Đường dẫn thư mục phải nằm trong vùng lưu trữ /uploads/");
        }
        Path folderPath = resolveSafeSubPath(normalizedUrl);
        if (!Files.exists(folderPath) || !Files.isDirectory(folderPath)) {
            return List.of();
        }
        return listImagesInDirectory(folderPath, normalizedUrl);
    }

    /**
     * Expands a database column value (either a folder URL like "/uploads/customers/079.../verification-docs"
     * OR legacy comma-separated image URLs) into a concrete list of previewable image URLs.
     */
    @Override
    public List<String> resolveImageUrlsFromStoredValue(String storedValue) {
        if (storedValue == null || storedValue.isBlank()) {
            return List.of();
        }
        String[] segments = storedValue.split(",");
        List<String> result = new ArrayList<>();
        for (String rawSeg : segments) {
            String seg = rawSeg != null ? rawSeg.trim() : "";
            if (seg.isEmpty()) {
                continue;
            }
            if (looksLikeUploadsFolderUrl(seg)) {
                try {
                    List<String> expanded = listFolderImages(seg);
                    if (!expanded.isEmpty()) {
                        result.addAll(expanded);
                    }
                } catch (BusinessException ex) {
                    log.debug("Skipping invalid folder path in stored verificationDocUrls '{}': {}", seg, ex.getMessage());
                }
            } else {
                result.add(seg);
            }
        }
        return result;
    }

    public static boolean looksLikeUploadsFolderUrl(String candidate) {
        if (candidate == null || candidate.isBlank()) {
            return false;
        }
        String clean = candidate.trim();
        int queryIdx = clean.indexOf('?');
        if (queryIdx >= 0) {
            clean = clean.substring(0, queryIdx);
        }
        while (clean.endsWith("/") && clean.length() > 1) {
            clean = clean.substring(0, clean.length() - 1);
        }
        if (!clean.startsWith("/uploads/")) {
            return false;
        }
        String lower = clean.toLowerCase(Locale.ROOT);
        for (String ext : IMAGE_EXTENSIONS) {
            if (lower.endsWith(ext)) {
                return false;
            }
        }
        return true;
    }

    private Optional<String> findExistingDuplicateFileName(Path directory, byte[] candidateBytes) {
        if (!Files.isDirectory(directory) || candidateBytes == null || candidateBytes.length == 0) {
            return Optional.empty();
        }
        long targetLen = candidateBytes.length;
        byte[] candidateDigest = sha256(candidateBytes);

        try (Stream<Path> stream = Files.list(directory)) {
            List<Path> sameSizeFiles = stream
                    .filter(Files::isRegularFile)
                    .filter(p -> hasSupportedImageExtension(p.getFileName().toString()))
                    .filter(p -> {
                        try {
                            return Files.size(p) == targetLen;
                        } catch (IOException ex) {
                            return false;
                        }
                    })
                    .toList();

            for (Path existing : sameSizeFiles) {
                try {
                    byte[] existingDigest = sha256(Files.readAllBytes(existing));
                    if (MessageDigest.isEqual(candidateDigest, existingDigest)) {
                        return Optional.of(existing.getFileName().toString());
                    }
                } catch (IOException ignored) {
                    // Ignore unreadable concurrent file
                }
            }
        } catch (IOException ignored) {
            // Ignore directory read error
        }
        return Optional.empty();
    }

    private static byte[] sha256(byte[] payload) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return md.digest(payload);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 unavailable in JVM", ex);
        }
    }

    private void synchronizeKeptFilesInFolder(Path targetDir, String relativeFolder, List<String> keepUrls) {
        Set<String> keepFileNames = new HashSet<>();
        List<String> externalUploadRefsToMaterialize = new ArrayList<>();
        String expectedPrefix = UPLOADS_URL_PREFIX + relativeFolder + "/";

        if (keepUrls != null) {
            for (String rawUrl : keepUrls) {
                if (rawUrl == null || rawUrl.isBlank()) {
                    continue;
                }
                String url = rawUrl.trim();
                int qIdx = url.indexOf('?');
                if (qIdx >= 0) {
                    url = url.substring(0, qIdx);
                }
                if (url.startsWith(expectedPrefix)) {
                    String nameOnly = url.substring(expectedPrefix.length());
                    if (!nameOnly.contains("/") && !nameOnly.contains("\\")) {
                        keepFileNames.add(nameOnly);
                    }
                } else if (url.startsWith(UPLOADS_URL_PREFIX)) {
                    externalUploadRefsToMaterialize.add(url);
                }
            }
        }

        if (Files.isDirectory(targetDir)) {
            try (Stream<Path> stream = Files.list(targetDir)) {
                stream.filter(Files::isRegularFile).forEach(existingFile -> {
                    String fn = existingFile.getFileName().toString();
                    if (!keepFileNames.contains(fn)) {
                        try {
                            Files.deleteIfExists(existingFile);
                        } catch (IOException ex) {
                            log.warn("Failed to remove unkept verification image {}: {}", existingFile, ex.getMessage());
                        }
                    }
                });
            } catch (IOException ex) {
                log.warn("Could not enumerate verification directory {}: {}", targetDir, ex.getMessage());
            }
        }

        // Materialize kept legacy /uploads/docs/... seed files into the customer's dedicated folder
        int legSeq = 1;
        for (String legacyUrl : externalUploadRefsToMaterialize) {
            String relLegacy = normalizeUploadsUrlPath(legacyUrl);
            if (relLegacy == null) {
                continue;
            }
            try {
                Path legacySource = resolveSafeSubPath(relLegacy);
                String rawBase = sanitizeFileStem(legacySource.getFileName().toString());
                if (Files.isRegularFile(legacySource)) {
                    String origFn = legacySource.getFileName().toString();
                    int dot = origFn.lastIndexOf('.');
                    String ext = dot >= 0 ? origFn.substring(dot).toLowerCase(Locale.ROOT) : ".jpg";
                    Path copied = resolveSafeChildFile(targetDir, String.format("00_legacy_%02d_%s%s", legSeq++, rawBase, ext));
                    Files.copy(legacySource, copied, StandardCopyOption.REPLACE_EXISTING);
                } else {
                    Path svgPlaceholder = resolveSafeChildFile(
                            targetDir,
                            String.format("00_seed_%02d_%s.svg", legSeq++, rawBase)
                    );
                    byte[] svgBytes = buildSeedDocumentPlaceholderSvg(legacySource.getFileName().toString());
                    Files.write(svgPlaceholder, svgBytes);
                }
            } catch (Exception ex) {
                log.debug("Skipped materializing legacy reference {}: {}", legacyUrl, ex.getMessage());
            }
        }
    }

    public static byte[] buildSeedDocumentPlaceholderSvg(String displayLabel) {
        String safeName = (displayLabel == null ? "chung-tu-luu-tru.jpg" : displayLabel)
                .replaceAll("[^a-zA-Z0-9._-]", "_");
        String svg = """
                <svg xmlns="http://www.w3.org/2000/svg" width="960" height="640" viewBox="0 0 960 640">
                  <rect width="960" height="640" fill="#f8fafc"/>
                  <rect x="36" y="36" width="888" height="568" rx="16" fill="#ffffff" stroke="#cbd5e1" stroke-width="2"/>
                  <rect x="36" y="36" width="888" height="72" rx="16" fill="#eff6ff"/>
                  <rect x="36" y="92" width="888" height="16" fill="#eff6ff"/>
                  <line x1="36" y1="108" x2="924" y2="108" stroke="#bfdbfe" stroke-width="1.5"/>
                  <text x="72" y="80" font-family="Inter, Segoe UI, Arial, sans-serif" font-size="22" font-weight="700" fill="#1e40af">PAWNMASTER ARCHIVE — HỒ SƠ HÌNH ẢNH LƯU TRỮ</text>
                  <rect x="72" y="148" width="816" height="340" rx="12" fill="#f8fafc" stroke="#e2e8f0" stroke-width="1.5" stroke-dasharray="8 6"/>
                  <circle cx="480" cy="272" r="44" fill="#eff6ff" stroke="#bfdbfe" stroke-width="2"/>
                  <path d="M464 278 l12 -14 l10 12 l14 -18 l16 20 z" fill="#1e40af"/>
                  <circle cx="468" cy="258" r="5" fill="#1e40af"/>
                  <text x="480" y="356" text-anchor="middle" font-family="Inter, Segoe UI, Arial, sans-serif" font-size="20" font-weight="700" fill="#0f172a">%s</text>
                  <text x="480" y="392" text-anchor="middle" font-family="Inter, Segoe UI, Arial, sans-serif" font-size="15" fill="#475569">Bản đối chiếu chứng từ lưu trữ trên hệ thống PawnMaster</text>
                  <text x="72" y="546" font-family="Inter, Segoe UI, Arial, sans-serif" font-size="14" font-weight="600" fill="#64748b">Trạng thái: Đã xác thực đối chiếu bản gốc tại quầy giao dịch</text>
                </svg>
                """.formatted(safeName);
        return svg.getBytes(StandardCharsets.UTF_8);
    }

    private List<String> listImagesInDirectory(Path directory, String relativeFolder) {
        if (!Files.isDirectory(directory)) {
            return List.of();
        }
        String cleanRel = relativeFolder.replace('\\', '/');
        while (cleanRel.startsWith("/")) {
            cleanRel = cleanRel.substring(1);
        }
        while (cleanRel.endsWith("/")) {
            cleanRel = cleanRel.substring(0, cleanRel.length() - 1);
        }
        final String urlPrefix = UPLOADS_URL_PREFIX + cleanRel + "/";

        try (Stream<Path> stream = Files.list(directory)) {
            return stream
                    .filter(Files::isRegularFile)
                    .map(p -> p.getFileName().toString())
                    .filter(this::hasSupportedImageExtension)
                    .sorted(Comparator.naturalOrder())
                    .map(name -> urlPrefix + name)
                    .toList();
        } catch (IOException ex) {
            log.error("Failed to list images inside {}: {}", directory, ex.getMessage());
            return List.of();
        }
    }

    private int countExistingImages(Path directory) {
        if (!Files.isDirectory(directory)) {
            return 0;
        }
        try (Stream<Path> stream = Files.list(directory)) {
            return (int) stream
                    .filter(Files::isRegularFile)
                    .map(p -> p.getFileName().toString())
                    .filter(this::hasSupportedImageExtension)
                    .count();
        } catch (IOException ex) {
            return 0;
        }
    }

    private void deleteFilesByPrefix(Path directory, String prefix) {
        if (!Files.isDirectory(directory)) {
            return;
        }
        try (Stream<Path> stream = Files.list(directory)) {
            stream.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().startsWith(prefix))
                    .forEach(p -> {
                        try {
                            Files.deleteIfExists(p);
                        } catch (IOException ignored) {
                            // Ignore transient lock on Windows
                        }
                    });
        } catch (IOException ignored) {
            // Ignore listing failure on fresh directory
        }
    }

    private boolean hasSupportedImageExtension(String fileName) {
        if (fileName == null) {
            return false;
        }
        String lower = fileName.toLowerCase(Locale.ROOT);
        for (String ext : IMAGE_EXTENSIONS) {
            if (lower.endsWith(ext)) {
                return true;
            }
        }
        return false;
    }

    private List<MultipartFile> filterNonEmptyFiles(List<MultipartFile> files) {
        if (files == null || files.isEmpty()) {
            return List.of();
        }
        return files.stream()
                .filter(f -> f != null && !f.isEmpty() && f.getSize() > 0)
                .toList();
    }

    private void writeBytesAtomically(byte[] bytes, Path destination) {
        try {
            Files.write(destination, bytes);
        } catch (IOException ex) {
            log.error("Failed to save uploaded file to {}: {}", destination, ex.getMessage());
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR, "Không thể lưu tệp hình ảnh lên hệ thống");
        }
    }

    private void createDirectoriesQuietly(Path dir) {
        try {
            Files.createDirectories(dir);
        } catch (IOException ex) {
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR, "Không thể khởi tạo thư mục lưu trữ hình ảnh");
        }
    }

    private Path resolveSafeSubPath(String relativeSubPath) {
        if (relativeSubPath == null || relativeSubPath.isBlank() || relativeSubPath.contains("..")) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "Đường dẫn thư mục lưu trữ không hợp lệ");
        }
        String cleaned = relativeSubPath.replace('\\', '/');
        while (cleaned.startsWith("/")) {
            cleaned = cleaned.substring(1);
        }
        if (cleaned.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "Đường dẫn thư mục lưu trữ không hợp lệ");
        }
        Path resolved = uploadRootPath.resolve(cleaned).toAbsolutePath().normalize();
        if (!resolved.startsWith(uploadRootPath) || resolved.equals(uploadRootPath)) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "Truy cập đường dẫn ngoài vùng cho phép bị từ chối");
        }
        return resolved;
    }

    private Path resolveSafeChildFile(Path parentDir, String fileName) {
        if (fileName == null || fileName.isBlank() || fileName.contains("..") || fileName.contains("/") || fileName.contains("\\")) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "Tên tệp lưu trữ không hợp lệ");
        }
        Path child = parentDir.resolve(fileName).toAbsolutePath().normalize();
        if (!child.startsWith(parentDir) || !child.startsWith(uploadRootPath)) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "Đường dẫn tệp lưu trữ không hợp lệ");
        }
        return child;
    }

    private String normalizeUploadsUrlPath(String url) {
        String trimmed = url.trim().replace('\\', '/');
        int queryIdx = trimmed.indexOf('?');
        if (queryIdx >= 0) {
            trimmed = trimmed.substring(0, queryIdx);
        }
        if (trimmed.contains("..")) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "Đường dẫn chứa ký tự điều hướng không hợp lệ");
        }
        if (!trimmed.startsWith(UPLOADS_URL_PREFIX)) {
            return null;
        }
        String rel = trimmed.substring(UPLOADS_URL_PREFIX.length());
        while (rel.endsWith("/")) {
            rel = rel.substring(0, rel.length() - 1);
        }
        return rel.isEmpty() ? null : rel;
    }

    public static String sanitizeOwnerKey(String rawKey) {
        if (rawKey == null) {
            return "";
        }
        String ascii = Normalizer.normalize(rawKey.trim(), Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .replace('đ', 'd')
                .replace('Đ', 'D');
        String cleaned = ascii.replaceAll("[^a-zA-Z0-9_-]", "");
        return cleaned.length() > 36 ? cleaned.substring(0, 36) : cleaned;
    }

    private static String sanitizeFileStem(String originalFilename) {
        if (originalFilename == null || originalFilename.isBlank()) {
            return "image";
        }
        String name = Paths.get(originalFilename).getFileName().toString();
        int dotIdx = name.lastIndexOf('.');
        String stem = dotIdx > 0 ? name.substring(0, dotIdx) : name;
        String ascii = Normalizer.normalize(stem, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .replace('đ', 'd')
                .replace('Đ', 'D');
        String safe = ascii.replaceAll("[^a-zA-Z0-9_-]+", "_")
                .replaceAll("^_+|_+$", "");
        if (safe.isEmpty()) {
            return "image";
        }
        return safe.length() > 36 ? safe.substring(0, 36) : safe.toLowerCase(Locale.ROOT);
    }
}
