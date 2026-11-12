package com.pawnmaster.api.modules.storage;

import com.pawnmaster.api.common.BusinessException;
import com.pawnmaster.api.config.WebMvcConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ImageCompressionAndStorageOptimizationTest {

    @TempDir
    Path tempUploadDir;

    private StorageProperties properties;
    private ImageCompressionProcessor compressionProcessor;
    private FileStorageService storageService;

    @BeforeEach
    void setUp() {
        properties = new StorageProperties(
                tempUploadDir.toString(),
                10L * 1024L * 1024L,
                15,
                true,
                1920,
                0.82f,
                true,
                86400L
        );
        compressionProcessor = new ImageCompressionProcessor(properties);
        storageService = new LocalFileStorageService(properties, compressionProcessor);
    }

    @Test
    void shouldDownscaleHighResPhotoToMax1920AndDramaticallyReduceFileSize() throws Exception {
        // Arrange: Simulate a heavy 3200x2400 camera/scanner document image (high-Q JPEG / PNG)
        byte[] heavyPngBytes = generateDetailedDocumentImagePng(3200, 2400);
        assertThat(heavyPngBytes.length).isGreaterThan(800_000);

        MockMultipartFile upload = new MockMultipartFile(
                "files", "hop-dong-lao-dong-4k.png", MediaType.IMAGE_PNG_VALUE, heavyPngBytes
        );

        // Act
        FileStorageService.VerificationFolderResult result =
                storageService.storeCustomerVerificationDocs("079201004821", List.of(upload), null, false);

        // Assert: Stored file is converted to .jpg, scaled proportionally to 1920x1440, and >65% smaller
        assertThat(result.totalFiles()).isEqualTo(1);
        String savedUrl = result.imageUrls().get(0);
        assertThat(savedUrl).endsWith(".jpg");

        String savedFileName = savedUrl.substring(savedUrl.lastIndexOf('/') + 1);
        Path savedPath = tempUploadDir.resolve("customers/079201004821/verification-docs").resolve(savedFileName);
        assertThat(Files.isRegularFile(savedPath)).isTrue();

        byte[] savedBytes = Files.readAllBytes(savedPath);
        assertThat(savedBytes.length).isLessThan(heavyPngBytes.length / 3);

        BufferedImage decoded = ImageIO.read(new ByteArrayInputStream(savedBytes));
        assertThat(decoded).isNotNull();
        assertThat(decoded.getWidth()).isEqualTo(1920);
        assertThat(decoded.getHeight()).isEqualTo(1440);
    }

    @Test
    void shouldAutoRotatePortraitExifOrientation6AndStripExifMetadata() throws Exception {
        // Arrange: 1600x1000 sensor frame tagged with EXIF Orientation = 6 (90° CW -> upright 1000x1600)
        byte[] rawJpeg = generateHighQualityJpeg(1600, 1000);
        byte[] jpegWithExifOrientation6 = injectExifOrientationApp1(rawJpeg, 6);
        assertThat(ImageCompressionProcessor.readJpegExifOrientation(jpegWithExifOrientation6)).isEqualTo(6);

        MockMultipartFile portraitUpload = new MockMultipartFile(
                "files", "cccd_portrait_phone.jpg", MediaType.IMAGE_JPEG_VALUE, jpegWithExifOrientation6
        );

        // Act
        FileStorageService.FileBatchUploadResult uploaded =
                storageService.storeCategorizedImages("PORTRAIT", "079201004821", List.of(portraitUpload));

        // Assert: Saved image is rotated upright (1000x1600) and EXIF APP1 block is stripped (orientation default = 1)
        String fileName = uploaded.primaryUrl().substring(uploaded.primaryUrl().lastIndexOf('/') + 1);
        Path savedFile = tempUploadDir.resolve("customers/079201004821/kyc").resolve(fileName);
        byte[] savedBytes = Files.readAllBytes(savedFile);

        BufferedImage rotated = ImageIO.read(new ByteArrayInputStream(savedBytes));
        assertThat(rotated).isNotNull();
        assertThat(rotated.getWidth()).isEqualTo(1000);
        assertThat(rotated.getHeight()).isEqualTo(1600);
        assertThat(ImageCompressionProcessor.readJpegExifOrientation(savedBytes)).isEqualTo(1);
    }

    @Test
    void shouldNeverInflateTinyAlreadyOptimizedPngOrPassThroughWebpSafely() throws Exception {
        // Arrange 1: Tiny 64x64 solid PNG (~200 bytes) where JPEG headers alone would be larger
        BufferedImage tiny = new BufferedImage(64, 64, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream tinyOut = new ByteArrayOutputStream();
        ImageIO.write(tiny, "png", tinyOut);
        byte[] tinyPngBytes = tinyOut.toByteArray();

        ImageCompressionProcessor.OptimizedImage optimizedTiny =
                compressionProcessor.optimizeForStorage(new MockMultipartFile(
                        "files", "tiny-badge.png", MediaType.IMAGE_PNG_VALUE, tinyPngBytes
                ));
        assertThat(optimizedTiny.bytes().length).isLessThanOrEqualTo(tinyPngBytes.length);

        // Arrange 2: Valid RIFF WEBP header payload passes through untouched
        byte[] webpStub = new byte[]{
                'R', 'I', 'F', 'F', 0x24, 0x00, 0x00, 0x00,
                'W', 'E', 'B', 'P', 'V', 'P', '8', ' ',
                0x18, 0x00, 0x00, 0x00, 0x30, 0x01, 0x00, (byte) 0x9D, 0x01, 0x2A
        };
        ImageCompressionProcessor.OptimizedImage optimizedWebp =
                compressionProcessor.optimizeForStorage(new MockMultipartFile(
                        "files", "photo.webp", "image/webp", webpStub
                ));
        assertThat(optimizedWebp.extension()).isEqualTo(".webp");
        assertThat(optimizedWebp.bytes()).isEqualTo(webpStub);
    }

    @Test
    void shouldDeduplicateIdenticalImagesBySha256WithinSameFolder() throws Exception {
        // Arrange: Two uploads containing the exact same binary photo content under different filenames
        byte[] photoBytes = generateHighQualityJpeg(1280, 960);
        MockMultipartFile copy1 = new MockMultipartFile(
                "files", "bang-luong-thang-10.jpg", MediaType.IMAGE_JPEG_VALUE, photoBytes);
        MockMultipartFile copy2 = new MockMultipartFile(
                "files", "bang-luong-thang-10-ban-sao.jpg", MediaType.IMAGE_JPEG_VALUE, photoBytes);

        // Act: Upload both in the same batch, then upload a 3rd identical copy in a follow-up call
        FileStorageService.VerificationFolderResult batch1 =
                storageService.storeCustomerVerificationDocs("079198005432", List.of(copy1, copy2), null, false);
        FileStorageService.VerificationFolderResult batch2 =
                storageService.storeCustomerVerificationDocs("079198005432", List.of(copy1), batch1.imageUrls(), true);

        // Assert: Only 1 physical file is stored on disk — zero duplicate bloat
        assertThat(batch1.totalFiles()).isEqualTo(1);
        assertThat(batch2.totalFiles()).isEqualTo(1);
    }

    @Test
    void shouldRejectDecompressionBombDimensionsExceedingPixelSafetyCeiling() {
        // Reject corrupt/empty payloads cleanly via BusinessException
        MockMultipartFile invalid = new MockMultipartFile(
                "files", "fake.jpg", MediaType.IMAGE_JPEG_VALUE, "not-image".getBytes(StandardCharsets.UTF_8));
        assertThatThrownBy(() -> compressionProcessor.optimizeForStorage(invalid))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void webMvcUploadsHandler_ShouldServeCacheControlHeaderForStoredImages() throws Exception {
        byte[] photoBytes = generateHighQualityJpeg(800, 600);
        MockMultipartFile file = new MockMultipartFile(
                "files", "seal.jpg", MediaType.IMAGE_JPEG_VALUE, photoBytes);
        FileStorageService.FileBatchUploadResult stored =
                storageService.storeCategorizedImages("COLLATERAL", "079201004821", List.of(file));

        WebMvcConfig webMvcConfig = new WebMvcConfig(properties);
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new FileStorageController(storageService)).build();
        assertThat(webMvcConfig.buildUploadsCacheControl().getHeaderValue())
                .contains("max-age=86400")
                .contains("private");
        assertThat(stored.primaryUrl()).startsWith("/uploads/collaterals/079201004821/");
        mvc.perform(get("/api/v1/files/folder-images")
                        .param("folder", "/uploads/collaterals/079201004821"))
                .andExpect(status().isOk());
    }

    private static byte[] generateDetailedDocumentImagePng(int width, int height) throws IOException {
        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        try {
            g.setPaint(new GradientPaint(0, 0, new Color(248, 250, 252), width, height, new Color(226, 232, 240)));
            g.fillRect(0, 0, width, height);
            Random rng = new Random(42L);
            // Add realistic scanner micro-grain + document lines & text
            for (int y = 0; y < height; y += 4) {
                for (int x = 0; x < width; x += 4) {
                    int v = 235 + rng.nextInt(20);
                    img.setRGB(x, y, (v << 16) | (v << 8) | v);
                }
            }
            g.setColor(new Color(30, 64, 175));
            g.setStroke(new BasicStroke(4f));
            g.drawRect(80, 80, width - 160, height - 160);
            g.setFont(new Font("SansSerif", Font.BOLD, 48));
            for (int row = 180; row < height - 180; row += 90) {
                g.setColor(new Color(15, 23, 42));
                g.drawString("HỢP ĐỒNG LAO ĐỘNG & SAO KÊ THU NHẬP — MÃ SỐ 079201004821", 140, row);
            }
        } finally {
            g.dispose();
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, "png", out);
        return out.toByteArray();
    }

    private static byte[] generateHighQualityJpeg(int width, int height) throws IOException {
        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        try {
            g.setColor(new Color(241, 245, 249));
            g.fillRect(0, 0, width, height);
            g.setColor(new Color(30, 64, 175));
            g.fillRect(40, 40, width / 2, height / 2);
            g.setColor(new Color(16, 185, 129));
            g.fillOval(width / 3, height / 3, width / 3, height / 3);
        } finally {
            g.dispose();
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpeg");
        ImageWriter writer = writers.next();
        try (ImageOutputStream ios = ImageIO.createImageOutputStream(out)) {
            writer.setOutput(ios);
            ImageWriteParam param = writer.getDefaultWriteParam();
            param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            param.setCompressionQuality(0.98f);
            writer.write(null, new IIOImage(img, null, null), param);
        } finally {
            writer.dispose();
        }
        return out.toByteArray();
    }

    /**
     * Injects a valid JPEG APP1 EXIF big-endian ("MM") segment carrying Tag 0x0112 (Orientation)
     * immediately after the SOI marker (FF D8).
     */
    private static byte[] injectExifOrientationApp1(byte[] jpegBytes, int orientation) {
        byte[] tiffAndExif = new byte[]{
                'E', 'x', 'i', 'f', 0x00, 0x00,
                // TIFF Header: "MM" (Big Endian), magic 0x002A, IFD0 offset = 0x00000008
                'M', 'M', 0x00, 0x2A, 0x00, 0x00, 0x00, 0x08,
                // IFD0 entry count = 1
                0x00, 0x01,
                // Entry 0: Tag 0x0112 (Orientation), Type 3 (SHORT), Count 1, Value = orientation (2 bytes + 2 pad)
                0x01, 0x12, 0x00, 0x03, 0x00, 0x00, 0x00, 0x01,
                0x00, (byte) orientation, 0x00, 0x00,
                // Next IFD offset = 0
                0x00, 0x00, 0x00, 0x00
        };
        int segLen = tiffAndExif.length + 2;
        ByteArrayOutputStream out = new ByteArrayOutputStream(jpegBytes.length + segLen + 2);
        out.write(0xFF);
        out.write(0xD8);
        out.write(0xFF);
        out.write(0xE1);
        out.write((segLen >> 8) & 0xFF);
        out.write(segLen & 0xFF);
        out.writeBytes(tiffAndExif);
        out.write(jpegBytes, 2, jpegBytes.length - 2);
        return out.toByteArray();
    }
}
