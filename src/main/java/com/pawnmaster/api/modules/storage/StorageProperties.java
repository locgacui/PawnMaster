package com.pawnmaster.api.modules.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Type-safe configuration properties for PawnMaster file storage, server-side image compression,
 * SHA-256 duplicate prevention, and HTTP browser caching.
 */
@ConfigurationProperties(prefix = "app.storage")
public record StorageProperties(
        String uploadDir,
        long maxFileSizeBytes,
        int maxFilesPerBatch,
        boolean imageOptimizationEnabled,
        int maxImageDimension,
        float jpegQuality,
        boolean deduplicateByHash,
        long cacheMaxAgeSeconds
) {

    public static final String DEFAULT_UPLOAD_DIR = "uploads";
    public static final long DEFAULT_MAX_FILE_SIZE_BYTES = 10L * 1024L * 1024L;
    public static final int DEFAULT_MAX_FILES_PER_BATCH = 15;
    public static final int DEFAULT_MAX_IMAGE_DIMENSION = 1920;
    public static final float DEFAULT_JPEG_QUALITY = 0.82f;
    public static final long DEFAULT_CACHE_MAX_AGE_SECONDS = 86_400L;

    public StorageProperties {
        uploadDir = (uploadDir == null || uploadDir.isBlank()) ? DEFAULT_UPLOAD_DIR : uploadDir.trim();
        maxFileSizeBytes = maxFileSizeBytes > 0 ? maxFileSizeBytes : DEFAULT_MAX_FILE_SIZE_BYTES;
        maxFilesPerBatch = maxFilesPerBatch > 0 ? maxFilesPerBatch : DEFAULT_MAX_FILES_PER_BATCH;
        maxImageDimension = maxImageDimension >= 320 ? maxImageDimension : DEFAULT_MAX_IMAGE_DIMENSION;
        jpegQuality = (jpegQuality > 0.10f && jpegQuality <= 1.0f) ? jpegQuality : DEFAULT_JPEG_QUALITY;
        cacheMaxAgeSeconds = cacheMaxAgeSeconds >= 0 ? cacheMaxAgeSeconds : DEFAULT_CACHE_MAX_AGE_SECONDS;
    }

    public static StorageProperties defaultsWithUploadDir(String customUploadDir) {
        return new StorageProperties(
                customUploadDir,
                DEFAULT_MAX_FILE_SIZE_BYTES,
                DEFAULT_MAX_FILES_PER_BATCH,
                true,
                DEFAULT_MAX_IMAGE_DIMENSION,
                DEFAULT_JPEG_QUALITY,
                true,
                DEFAULT_CACHE_MAX_AGE_SECONDS
        );
    }
}

