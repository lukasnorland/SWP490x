package com.funix.swp490x.mrs.catalog;

import com.funix.swp490x.mrs.aws.AwsCredentialsFactory;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.util.StringUtils;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

/** Selects the local catalog store when {@code mrs.catalog.local-dir} is set, otherwise S3. */
@Configuration
@EnableConfigurationProperties(CatalogProperties.class)
public class CatalogConfig {

    private static final Logger log = LoggerFactory.getLogger(CatalogConfig.class);

    /** Defers AWS credential resolution until the S3 client is needed. */
    @Bean(destroyMethod = "close")
    @ConditionalOnMissingBean(S3Client.class)
    @Lazy
    public S3Client catalogS3Client(CatalogProperties properties) {
        return S3Client.builder()
                .region(Region.of(properties.getRegion()))
                .credentialsProvider(AwsCredentialsFactory.forProfile(
                        properties.getAwsProfile(), "mrs.catalog.aws-profile"))
                .build();
    }

    @Bean
    @ConditionalOnMissingBean(CatalogObjectStore.class)
    public CatalogObjectStore catalogObjectStore(CatalogProperties properties,
            @Lazy S3Client s3Client) {

        if (StringUtils.hasText(properties.getLocalDir())) {
            Path root = Path.of(properties.getLocalDir().trim()).toAbsolutePath();
            try {
                Files.createDirectories(root);
            } catch (IOException e) {
                log.warn("Could not create mrs.catalog.local-dir {}; listing will stay empty until it exists",
                        root, e);
            }
            log.info("Catalog import reads from the local directory {}", root);
            return new LocalDirectoryCatalogObjectStore(root);
        }

        return new S3CatalogObjectStore(s3Client, properties.getBucket(), properties.getPrefix());
    }
}
