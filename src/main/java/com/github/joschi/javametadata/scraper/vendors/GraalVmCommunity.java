package com.github.joschi.javametadata.scraper.vendors;

import com.github.joschi.javametadata.scraper.Scraper;
import com.github.joschi.javametadata.model.JdkMetadata;
import java.nio.file.Path;
import java.util.List;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Scraper for GraalVM Community releases (starting from GraalVM 23) */
public class GraalVmCommunity extends GraalVmBaseScraper {
    private static final String VENDOR = "graalvm-community";
    private static final String GITHUB_ORG = "graalvm";
    private static final String GITHUB_REPO = "graalvm-ce-builds";
    
    // Starting from graalvm 23: graalvm-community-jdk-17.0.7_macos-aarch64_bin.tar.gz
    // or: graalvm-community-jdk-17.0.7_linux-x64_bin.tar.gz
    private static final Pattern FILENAME_PATTERN = Pattern.compile(
            "^graalvm-community-jdk-(\\d{1,2}\\.\\d{1}\\.\\d{1,3})_(linux|macos|windows)-(aarch64|x64)_bin\\.(zip|tar\\.gz)$");

    public GraalVmCommunity(Path metadataDir, Path checksumDir, Logger logger) {
        super(metadataDir, checksumDir, logger);
    }


    @Override
    protected String getGithubOrg() {
        return GITHUB_ORG;
    }

    @Override
    protected String getGithubRepo() {
        return GITHUB_REPO;
    }

    @Override
    protected boolean shouldProcessTag(String tagName) {
        return tagName.startsWith("jdk");
    }

    @Override
    protected boolean shouldProcessAsset(String assetName) {
        return assetName.startsWith("graalvm-community") && 
               (assetName.endsWith("tar.gz") || assetName.endsWith("zip"));
    }

    @Override
    protected void processAsset(String tagName, String assetName, List<JdkMetadata> allMetadata)
            throws Exception {
        
        if (metadataExists(assetName)) {
            log("Skipping " + assetName + " (already exists)");
            return;
        }

        Matcher matcher = FILENAME_PATTERN.matcher(assetName);
        if (!matcher.matches()) {
            log("Skipping " + assetName + " (does not match pattern)");
            return;
        }

        String javaVersion = matcher.group(1);
        String os = matcher.group(2);
        String arch = matcher.group(3);
        String ext = matcher.group(4);

        String url =
                String.format(
                        "https://github.com/%s/%s/releases/download/%s/%s",
                        GITHUB_ORG, GITHUB_REPO, tagName, assetName);

        // Download and compute hashes
        DownloadResult download = downloadFile(url, assetName);

        // Create metadata
        JdkMetadata metadata = createMetadata(
                VENDOR,
                assetName,
                "ga",
                javaVersion,
                javaVersion,
                os,
                arch,
                ext,
                url,
                download);

        saveMetadataFile(metadata);
        allMetadata.add(metadata);
        log("Processed " + assetName);
    }

    public static class Discovery implements Scraper.Discovery {
        @Override
        public String name() {
            return "graalvm-community";
        }

        @Override
        public String vendor() {
            return "graalvm-community";
        }

        @Override
        public Scraper create(Path metadataDir, Path checksumDir, Logger logger) {
            return new GraalVmCommunity(metadataDir, checksumDir, logger);
        }
    }

}
