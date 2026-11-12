package com.pawnmaster.api.modules.storage;

import com.pawnmaster.api.common.BusinessException;
import com.pawnmaster.api.common.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.plugins.jpeg.JPEGImageWriteParam;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Iterator;

/**
 * Validates, auto-orients (EXIF Tag 0x0112), downscales to bounded Full-HD dimensions,
 * strips camera EXIF/GPS metadata, and compresses uploaded images into progressive JPEGs.
 */
@Slf4j
@Component
public class ImageCompressionProcessor {

    private static final long MAX_DECODED_PIXELS = 64_000_000L; // 64 MP anti-decompression-bomb ceiling
    private static final int EXIF_ORIENTATION_TAG = 0x0112;

    private final StorageProperties properties;

    public ImageCompressionProcessor(StorageProperties properties) {
        this.properties = properties;
    }

    public record OptimizedImage(
            byte[] bytes,
            String extension,
            boolean reEncoded
    ) {
    }

    public OptimizedImage optimizeForStorage(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "Tệp hình ảnh tải lên bị rỗng");
        }
        if (file.getSize() > properties.maxFileSizeBytes()) {
            long maxMb = properties.maxFileSizeBytes() / (1024L * 1024L);
            throw new BusinessException(
                    ErrorCode.INVALID_REQUEST,
                    "Kích thước tệp vượt quá giới hạn " + maxMb + "MB: " + file.getOriginalFilename()
            );
        }

        byte[] rawBytes;
        try {
            rawBytes = file.getBytes();
        } catch (IOException ex) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "Không thể đọc nội dung tệp hình ảnh tải lên");
        }

        String detectedExt = detectExtensionFromMagicBytes(rawBytes);

        // Preserve animated GIFs, WebP payloads, or disabled optimization mode as-is
        if (!properties.imageOptimizationEnabled()
                || ".gif".equals(detectedExt)
                || ".webp".equals(detectedExt)) {
            return new OptimizedImage(rawBytes, detectedExt, false);
        }

        verifySafeImageDimensions(rawBytes);

        BufferedImage decoded;
        try {
            decoded = ImageIO.read(new ByteArrayInputStream(rawBytes));
        } catch (IOException | RuntimeException ex) {
            log.debug("Skipping re-encoding for undecodable image header ({}): {}",
                    file.getOriginalFilename(), ex.getMessage());
            return new OptimizedImage(rawBytes, detectedExt, false);
        }

        if (decoded == null || decoded.getWidth() <= 0 || decoded.getHeight() <= 0) {
            // Minimal magic-byte stubs or unsupported subformats fall back safely to validated bytes
            return new OptimizedImage(rawBytes, detectedExt, false);
        }

        int exifOrientation = ".jpg".equals(detectedExt) ? readJpegExifOrientation(rawBytes) : 1;
        int maxDim = properties.maxImageDimension();

        boolean swapsAxes = exifOrientation >= 5 && exifOrientation <= 8;
        int uprightWidth = swapsAxes ? decoded.getHeight() : decoded.getWidth();
        int uprightHeight = swapsAxes ? decoded.getWidth() : decoded.getHeight();

        double scale = 1.0;
        int longestSide = Math.max(uprightWidth, uprightHeight);
        if (longestSide > maxDim) {
            scale = (double) maxDim / (double) longestSide;
        }

        int targetWidth = Math.max(1, (int) Math.round(uprightWidth * scale));
        int targetHeight = Math.max(1, (int) Math.round(uprightHeight * scale));

        BufferedImage rgbCanvas = renderOrientedAndScaledRgb(
                decoded, exifOrientation, uprightWidth, uprightHeight, targetWidth, targetHeight
        );

        byte[] compressedJpeg;
        try {
            compressedJpeg = encodeProgressiveJpeg(rgbCanvas, properties.jpegQuality());
        } catch (IOException ex) {
            log.warn("Progressive JPEG compression failed for {}, falling back to raw bytes: {}",
                    file.getOriginalFilename(), ex.getMessage());
            return new OptimizedImage(rawBytes, detectedExt, false);
        }

        boolean transformedGeometry = (exifOrientation != 1) || (scale < 1.0);
        if (!transformedGeometry && compressedJpeg.length >= rawBytes.length) {
            return new OptimizedImage(rawBytes, detectedExt, false);
        }

        if (log.isDebugEnabled()) {
            int savedPct = rawBytes.length > 0
                    ? (int) Math.round((1.0 - ((double) compressedJpeg.length / rawBytes.length)) * 100.0)
                    : 0;
            log.debug("optimized_upload file={} origBytes={} finalBytes={} savedPct={}% dims={}x{}->{}x{} exifOrientation={}",
                    file.getOriginalFilename(),
                    rawBytes.length,
                    compressedJpeg.length,
                    savedPct,
                    decoded.getWidth(),
                    decoded.getHeight(),
                    targetWidth,
                    targetHeight,
                    exifOrientation);
        }

        return new OptimizedImage(compressedJpeg, ".jpg", true);
    }

    public static String detectExtensionFromMagicBytes(byte[] data) {
        if (data == null || data.length < 3) {
            throw new BusinessException(
                    ErrorCode.INVALID_REQUEST,
                    "Định dạng tệp không hợp lệ (chỉ chấp nhận ảnh thật JPG, PNG, WEBP, GIF)"
            );
        }
        if ((data[0] & 0xFF) == 0xFF
                && (data[1] & 0xFF) == 0xD8
                && (data[2] & 0xFF) == 0xFF) {
            return ".jpg";
        }
        if (data.length >= 8
                && (data[0] & 0xFF) == 0x89
                && data[1] == 0x50
                && data[2] == 0x4E
                && data[3] == 0x47
                && data[4] == 0x0D
                && data[5] == 0x0A
                && data[6] == 0x1A
                && data[7] == 0x0A) {
            return ".png";
        }
        if (data.length >= 6
                && data[0] == 'G'
                && data[1] == 'I'
                && data[2] == 'F'
                && data[3] == '8'
                && (data[4] == '7' || data[4] == '9')
                && data[5] == 'a') {
            return ".gif";
        }
        if (data.length >= 12
                && data[0] == 'R'
                && data[1] == 'I'
                && data[2] == 'F'
                && data[3] == 'F'
                && data[8] == 'W'
                && data[9] == 'E'
                && data[10] == 'B'
                && data[11] == 'P') {
            return ".webp";
        }

        throw new BusinessException(
                ErrorCode.INVALID_REQUEST,
                "Định dạng tệp không hợp lệ (chỉ chấp nhận ảnh thật JPG, PNG, WEBP, GIF)"
        );
    }

    private void verifySafeImageDimensions(byte[] rawBytes) {
        try (ImageInputStream iis = ImageIO.createImageInputStream(new ByteArrayInputStream(rawBytes))) {
            if (iis == null) {
                return;
            }
            Iterator<ImageReader> readers = ImageIO.getImageReaders(iis);
            if (!readers.hasNext()) {
                return;
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(iis, true, true);
                long width = reader.getWidth(0);
                long height = reader.getHeight(0);
                if (width <= 0 || height <= 0 || (width * height) > MAX_DECODED_PIXELS) {
                    throw new BusinessException(
                            ErrorCode.INVALID_REQUEST,
                            "Độ phân giải hình ảnh vượt ngưỡng an toàn xử lý bộ nhớ (tối đa 64MP)"
                    );
                }
            } finally {
                reader.dispose();
            }
        } catch (BusinessException ex) {
            throw ex;
        } catch (IOException | RuntimeException ignored) {
            // Header-only stubs without full frame headers are handled in ImageIO.read fallback
        }
    }

    private static BufferedImage renderOrientedAndScaledRgb(
            BufferedImage src,
            int orientation,
            int uprightWidth,
            int uprightHeight,
            int targetWidth,
            int targetHeight
    ) {
        BufferedImage canvas = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2d = canvas.createGraphics();
        try {
            g2d.setColor(Color.WHITE);
            g2d.fillRect(0, 0, targetWidth, targetHeight);
            g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            double sx = (double) targetWidth / (double) uprightWidth;
            double sy = (double) targetHeight / (double) uprightHeight;

            AffineTransform tx = new AffineTransform();
            tx.scale(sx, sy);

            int w = src.getWidth();
            int h = src.getHeight();
            switch (orientation) {
                case 2 -> {
                    tx.scale(-1.0, 1.0);
                    tx.translate(-w, 0);
                }
                case 3 -> {
                    tx.translate(w, h);
                    tx.rotate(Math.PI);
                }
                case 4 -> {
                    tx.scale(1.0, -1.0);
                    tx.translate(0, -h);
                }
                case 5 -> {
                    tx.rotate(Math.PI / 2.0);
                    tx.scale(1.0, -1.0);
                }
                case 6 -> {
                    tx.translate(h, 0);
                    tx.rotate(Math.PI / 2.0);
                }
                case 7 -> {
                    tx.scale(-1.0, 1.0);
                    tx.translate(-h, 0);
                    tx.translate(0, w);
                    tx.rotate(3.0 * Math.PI / 2.0);
                }
                case 8 -> {
                    tx.translate(0, w);
                    tx.rotate(3.0 * Math.PI / 2.0);
                }
                default -> {
                    // Orientation 1: normal upright
                }
            }

            g2d.drawImage(src, tx, null);
        } finally {
            g2d.dispose();
        }
        return canvas;
    }

    private static byte[] encodeProgressiveJpeg(BufferedImage rgbImage, float quality) throws IOException {
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpeg");
        if (!writers.hasNext()) {
            throw new IOException("No JPEG ImageWriter registered in current JVM");
        }
        ImageWriter writer = writers.next();
        ByteArrayOutputStream baos = new ByteArrayOutputStream(64 * 1024);
        try (ImageOutputStream ios = ImageIO.createImageOutputStream(baos)) {
            writer.setOutput(ios);
            ImageWriteParam writeParam = writer.getDefaultWriteParam();
            if (writeParam.canWriteCompressed()) {
                writeParam.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                writeParam.setCompressionQuality(quality);
            }
            if (writeParam.canWriteProgressive()) {
                writeParam.setProgressiveMode(ImageWriteParam.MODE_DEFAULT);
            }
            if (writeParam instanceof JPEGImageWriteParam jpegParam) {
                jpegParam.setOptimizeHuffmanTables(true);
            }
            // Passing null streamMetadata & imageMetadata guarantees stripped EXIF/GPS metadata
            writer.write(null, new IIOImage(rgbImage, null, null), writeParam);
        } finally {
            writer.dispose();
        }
        return baos.toByteArray();
    }

    /**
     * Reads JPEG APP1 EXIF Orientation tag (0x0112) without external libraries.
     * Returns 1 (normal) if no EXIF block is present or if malformed.
     */
    public static int readJpegExifOrientation(byte[] jpeg) {
        if (jpeg == null || jpeg.length < 14
                || (jpeg[0] & 0xFF) != 0xFF || (jpeg[1] & 0xFF) != 0xD8) {
            return 1;
        }
        int offset = 2;
        while (offset + 4 <= jpeg.length) {
            if ((jpeg[offset] & 0xFF) != 0xFF) {
                return 1;
            }
            int marker = jpeg[offset + 1] & 0xFF;
            while (marker == 0xFF && offset + 2 < jpeg.length) {
                offset++;
                marker = jpeg[offset + 1] & 0xFF;
            }
            // SOS (Start of Scan) or EOI: image bitstream starts, stop marker scanning
            if (marker == 0xDA || marker == 0xD9) {
                return 1;
            }
            if (offset + 4 > jpeg.length) {
                return 1;
            }
            int segmentLength = readUint16(jpeg, offset + 2, false);
            if (segmentLength < 2 || offset + 2 + segmentLength > jpeg.length) {
                return 1;
            }
            if (marker == 0xE1 && segmentLength >= 16) {
                int payloadStart = offset + 4;
                if (jpeg[payloadStart] == 'E'
                        && jpeg[payloadStart + 1] == 'x'
                        && jpeg[payloadStart + 2] == 'i'
                        && jpeg[payloadStart + 3] == 'f'
                        && jpeg[payloadStart + 4] == 0
                        && jpeg[payloadStart + 5] == 0) {
                    int tiffStart = payloadStart + 6;
                    int tiffEnd = offset + 2 + segmentLength;
                    return parseTiffOrientation(jpeg, tiffStart, tiffEnd);
                }
            }
            offset += 2 + segmentLength;
        }
        return 1;
    }

    private static int parseTiffOrientation(byte[] data, int tiffStart, int tiffEnd) {
        if (tiffStart + 8 > tiffEnd) {
            return 1;
        }
        boolean littleEndian;
        if (data[tiffStart] == 'I' && data[tiffStart + 1] == 'I') {
            littleEndian = true;
        } else if (data[tiffStart] == 'M' && data[tiffStart + 1] == 'M') {
            littleEndian = false;
        } else {
            return 1;
        }

        if (readUint16(data, tiffStart + 2, littleEndian) != 0x002A) {
            return 1;
        }
        int ifdOffset = readInt32(data, tiffStart + 4, littleEndian);
        if (ifdOffset < 8) {
            return 1;
        }
        int ifdPos = tiffStart + ifdOffset;
        if (ifdPos + 2 > tiffEnd) {
            return 1;
        }
        int entryCount = readUint16(data, ifdPos, littleEndian);
        int cursor = ifdPos + 2;
        for (int i = 0; i < entryCount && cursor + 12 <= tiffEnd; i++, cursor += 12) {
            int tag = readUint16(data, cursor, littleEndian);
            if (tag == EXIF_ORIENTATION_TAG) {
                int value = readUint16(data, cursor + 8, littleEndian);
                return (value >= 1 && value <= 8) ? value : 1;
            }
        }
        return 1;
    }

    private static int readUint16(byte[] buf, int pos, boolean littleEndian) {
        if (littleEndian) {
            return (buf[pos] & 0xFF) | ((buf[pos + 1] & 0xFF) << 8);
        }
        return ((buf[pos] & 0xFF) << 8) | (buf[pos + 1] & 0xFF);
    }

    private static int readInt32(byte[] buf, int pos, boolean littleEndian) {
        if (littleEndian) {
            return (buf[pos] & 0xFF)
                    | ((buf[pos + 1] & 0xFF) << 8)
                    | ((buf[pos + 2] & 0xFF) << 16)
                    | ((buf[pos + 3] & 0xFF) << 24);
        }
        return ((buf[pos] & 0xFF) << 24)
                | ((buf[pos + 1] & 0xFF) << 16)
                | ((buf[pos + 2] & 0xFF) << 8)
                | (buf[pos + 3] & 0xFF);
    }
}
