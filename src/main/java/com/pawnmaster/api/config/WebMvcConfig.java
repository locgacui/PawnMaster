package com.pawnmaster.api.config;

import com.pawnmaster.api.modules.storage.LocalFileStorageService;
import com.pawnmaster.api.modules.storage.StorageProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.lang.NonNull;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

import java.io.IOException;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.Locale;

@Configuration
@EnableConfigurationProperties(StorageProperties.class)
public class WebMvcConfig implements WebMvcConfigurer {

    private final StorageProperties storageProperties;

    @Autowired
    public WebMvcConfig(StorageProperties storageProperties) {
        this.storageProperties = storageProperties != null
                ? storageProperties
                : StorageProperties.defaultsWithUploadDir(null);
    }

    public WebMvcConfig() {
        this(StorageProperties.defaultsWithUploadDir(null));
    }

    public CacheControl buildUploadsCacheControl() {
        long seconds = storageProperties.cacheMaxAgeSeconds();
        if (seconds <= 0) {
            return CacheControl.noCache();
        }
        return CacheControl.maxAge(Duration.ofSeconds(seconds)).cachePrivate();
    }

    @Override
    public void addCorsMappings(@NonNull CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOriginPatterns("*")
                .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true)
                .maxAge(3600);
    }

    @Override
    public void addResourceHandlers(@NonNull ResourceHandlerRegistry registry) {
        String uploadLocationUri = Paths.get(storageProperties.uploadDir())
                .toAbsolutePath()
                .normalize()
                .toUri()
                .toString();
        if (!uploadLocationUri.endsWith("/")) {
            uploadLocationUri = uploadLocationUri + "/";
        }

        registry.addResourceHandler("/uploads/**")
                .addResourceLocations(uploadLocationUri)
                .setCacheControl(buildUploadsCacheControl())
                .resourceChain(false)
                .addResolver(new PathResourceResolver() {
                    @Override
                    protected Resource getResource(@NonNull String resourcePath, @NonNull Resource location) throws IOException {
                        Resource physical = super.getResource(resourcePath, location);
                        if (physical != null && physical.exists() && physical.isReadable()) {
                            return physical;
                        }
                        // Fallback preview card for pre-seeded demo paths in data.sql (/uploads/docs/..., /uploads/cccd/...)
                        String lower = resourcePath.toLowerCase(Locale.ROOT);
                        if (!resourcePath.contains("..")
                                && (lower.endsWith(".jpg") || lower.endsWith(".jpeg")
                                || lower.endsWith(".png") || lower.endsWith(".webp") || lower.endsWith(".svg"))) {
                            String fileName = Paths.get(resourcePath).getFileName().toString();
                            byte[] svgBytes = LocalFileStorageService.buildSeedDocumentPlaceholderSvg(fileName);
                            return new ByteArrayResource(svgBytes) {
                                @Override
                                public String getFilename() {
                                    return "seed-preview.svg";
                                }
                            };
                        }
                        return null;
                    }
                });
    }
}
