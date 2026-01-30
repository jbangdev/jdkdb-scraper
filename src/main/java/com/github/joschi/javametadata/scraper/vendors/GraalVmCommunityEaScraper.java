package com.github.joschi.javametadata.scraper.vendors;

import com.fasterxml.jackson.databind.JsonNode;
import com.github.joschi.javametadata.scraper.Scraper;
import com.github.joschi.javametadata.model.JdkMetadata;
import com.github.joschi.javametadata.scraper.BaseScraper;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Scraper for GraalVM Community Early Access releases from graalvm-ce-dev-builds repository */
public class GraalVmCommunityEaScraper extends BaseScraper {
    private static final String VENDOR = "graalvm-community";
    private static final String GITHUB_ORG = "graalvm";
    private static final String GITHUB_REPO = "graalvm-ce-dev-builds";
    private static final String GITHUB_API_BASE = "https://api.github.com/repos";
    
    // Pattern for community dev builds: graalvm-community-jdk-17.0.8_linux-x64_bin.tar.gz
    // or with build number: graalvm-community-jdk-21.0.1-dev_linux-x64_bin.tar.gz
    private static final Pattern FILENAME_PATTERN = Pattern.compile(
            "^graalvm-community-jdk-(\\d{1,2}\\.\\d{1}\\.\\d{1,3}(?:-dev)?)_(linux|macos|windows)-(aarch64|x64)_bin\\.(zip|tar\\.gz)$");

    public GraalVmCommunityEaScraper(Path metadataDir, Path checksumDir, Logger logger) {
        super(metadataDir, checksumDir, logger);
    }

    @Override
    protected List<JdkMetadata> scrape() throws Exception {
        List<JdkMetadata> allMetadata = new ArrayList<>();

        log("Fetching EA releases from GitHub");
        String releasesUrl = String.format("%s/%s/%s/releases?per_page=100", 
                GITHUB_API_BASE, GITHUB_ORG, GITHUB_REPO);
        
        String json = httpUtils.downloadString(releasesUrl);
        JsonNode releases = objectMapper.readTree(json);

        if (!releases.isArray()) {
            log("No releases found");
            return allMetadata;
        }

        for (JsonNode release : releases) {
            // Only process prereleases (EA releases)
            boolean isPrerelease = release.path("prerelease").asBoolean(false);
            if (!isPrerelease) {
                continue;
            }

            String tagName = release.path("tag_name").asText();
            
            // Only process Community releases (which start with "jdk")
            if (!tagName.startsWith("jdk")) {
                continue;
            }

            log("Processing EA release: " + tagName);

            JsonNode assets = release.path("assets");
            if (!assets.isArray()) {
                continue;
            }

            for (JsonNode asset : assets) {
                String assetName = asset.path("name").asText();

                if (!assetName.startsWith("graalvm-community") || 
                    (!assetName.endsWith("tar.gz") && !assetName.endsWith("zip"))) {
                    continue;
                }

                if (metadataExists(assetName)) {
                    log("Skipping " + assetName + " (already exists)");
                    continue;
                }

                try {
                    processAsset(tagName, assetName, allMetadata);
                } catch (Exception e) {
                    log("Failed to process " + assetName + ": " + e.getMessage());
                }
            }
        }

        return allMetadata;
    }

    private void processAsset(String tagName, String assetName, List<JdkMetadata> allMetadata)
            throws Exception {

        Matcher matcher = FILENAME_PATTERN.matcher(assetName);
        if (!matcher.matches()) {
            log("Skipping " + assetName + " (does not match pattern)");
            return;
        }

        String javaVersion = matcher.group(1);
        String os = matcher.group(2);
        String arch = matcher.group(3);
        String ext = matcher.group(4);

        String url = String.format(
                "https://github.com/%s/%s/releases/download/%s/%s",
                GITHUB_ORG, GITHUB_REPO, tagName, assetName);

        // Download and compute hashes
        DownloadResult download = downloadFile(url, assetName);

        // Create metadata
        JdkMetadata metadata = new JdkMetadata();
        metadata.setVendor(VENDOR);
        metadata.setFilename(assetName);
        metadata.setReleaseType("ea");
        metadata.setVersion(javaVersion);
        metadata.setJavaVersion(javaVersion);
        metadata.setJvmImpl("graalvm");
        metadata.setOs(normalizeOs(os));
        metadata.setArchitecture(normalizeArch(arch));
        metadata.setFileType(ext);
        metadata.setImageType("jdk");
        metadata.setFeatures(new ArrayList<>());
        metadata.setUrl(url);
        metadata.setMd5(download.md5());
        metadata.setMd5File(assetName + ".md5");
        metadata.setSha1(download.sha1());
        metadata.setSha1File(assetName + ".sha1");
        metadata.setSha256(download.sha256());
        metadata.setSha256File(assetName + ".sha256");
        metadata.setSha512(download.sha512());
        metadata.setSha512File(assetName + ".sha512");
        metadata.setSize(download.size());

        saveMetadataFile(metadata);
        allMetadata.add(metadata);
        log("Processed " + assetName);
    }

    public static class Discovery implements Scraper.Discovery {
        @Override
        public String name() {
            return "graalvm-community-ea";
        }

        @Override
        public String vendor() {
            return "graalvm-community";
        }

        @Override
        public Scraper create(Path metadataDir, Path checksumDir, Logger logger) {
            return new GraalVmCommunityEaScraper(metadataDir, checksumDir, logger);
        }
    }
}
