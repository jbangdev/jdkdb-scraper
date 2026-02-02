package com.github.joschi.javametadata.scraper.vendors;

import com.github.joschi.javametadata.scraper.Scraper;
import com.fasterxml.jackson.databind.JsonNode;
import com.github.joschi.javametadata.model.JdkMetadata;
import com.github.joschi.javametadata.scraper.AdoptiumMarketplaceScraper;

import java.nio.file.Path;
import java.util.List;
import java.util.logging.Logger;

/** Scraper for Huawei BiSheng JDK releases */
public class Bisheng extends AdoptiumMarketplaceScraper {
    private static final String VENDOR = "bisheng";

    public Bisheng(Path metadataDir, Path checksumDir, Logger logger) {
        super(metadataDir, checksumDir, logger);
    }


    @Override
    public String getVendorName() {
        return VENDOR;
    }

    @Override
    protected String getApiBase() {
        return "https://marketplace-api.adoptium.net/v1";
    }

    @Override
    protected String getAvailableReleasesPath() {
        return "/info/available_releases/huawei";
    }

    @Override
    protected String getAssetsPathTemplate() {
        return "/assets/feature_releases/huawei/%d";
    }

    @Override
    protected String extractJavaVersion(JsonNode asset) {
        return asset.path("openjdk_version_data").path("openjdk_version").asText();
    }

    @Override
    protected String extractVersion(JsonNode asset) {
        String releaseName = asset.path("release_name").asText();
        // Version is release_name with first 3 characters removed (e.g., "jdk-11.0.10" -> "11.0.10")
        return releaseName.length() > 3 ? releaseName.substring(3) : releaseName;
    }

    @Override
    protected void processBinary(
            JsonNode binary, String version, String javaVersion, List<JdkMetadata> allMetadata)
            throws Exception {
        createStandardMetadata(binary, version, javaVersion, allMetadata, List.of());
    }

    public static class Discovery implements Scraper.Discovery {
        @Override
        public String name() {
            return "bisheng";
        }

        @Override
        public String vendor() {
            return "bisheng";
        }

        @Override
        public Scraper create(Path metadataDir, Path checksumDir, Logger logger) {
            return new Bisheng(metadataDir, checksumDir, logger);
        }
    }

}
