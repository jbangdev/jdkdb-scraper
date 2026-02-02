package com.github.joschi.javametadata.scraper.vendors;

import com.github.joschi.javametadata.scraper.Scraper;
import com.fasterxml.jackson.databind.JsonNode;
import com.github.joschi.javametadata.model.JdkMetadata;
import com.github.joschi.javametadata.scraper.AdoptiumMarketplaceScraper;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

/** Scraper for AdoptOpenJDK releases (legacy, now part of Adoptium) */
public class AdoptOpenJdk extends AdoptiumMarketplaceScraper {
    private static final String VENDOR = "adoptopenjdk";

    public AdoptOpenJdk(Path metadataDir, Path checksumDir, Logger logger) {
        super(metadataDir, checksumDir, logger);
    }


    @Override
    public String getVendorName() {
        return VENDOR;
    }

    @Override
    protected String getApiBase() {
        return "https://api.adoptopenjdk.net/v3";
    }

    @Override
    protected String getAvailableReleasesPath() {
        return "/info/available_releases";
    }

    @Override
    protected String getAssetsPathTemplate() {
        return "/assets/feature_releases/%d/ga&project=jdk&vendor=adoptopenjdk";
    }

    @Override
    protected String extractJavaVersion(JsonNode asset) {
        return asset.path("version_data").path("openjdk_version").asText();
    }

    @Override
    protected String extractVersion(JsonNode asset) {
        return asset.path("version_data").path("semver").asText();
    }

    @Override
    protected void processBinary(
            JsonNode binary, String version, String javaVersion, List<JdkMetadata> allMetadata)
            throws Exception {

        String heapSize = binary.path("heap_size").asText();
        String os = binary.path("os").asText();

        // Build additional features
        List<String> additionalFeatures = new ArrayList<>();
        if (heapSize.equals("large")) {
            additionalFeatures.add("large_heap");
        }
        if (os.equals("alpine-linux")) {
            additionalFeatures.add("musl");
        }

        createStandardMetadata(binary, version, javaVersion, allMetadata, additionalFeatures);
    }

    public static class Discovery implements Scraper.Discovery {
        @Override
        public String name() {
            return "adoptopenjdk";
        }

        @Override
        public String vendor() {
            return "adoptopenjdk";
        }

        @Override
        public Scraper create(Path metadataDir, Path checksumDir, Logger logger) {
            return new AdoptOpenJdk(metadataDir, checksumDir, logger);
        }
    }

}
