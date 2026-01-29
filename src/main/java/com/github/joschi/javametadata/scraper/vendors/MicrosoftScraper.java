package com.github.joschi.javametadata.scraper.vendors;

import com.github.joschi.javametadata.model.JdkMetadata;
import com.github.joschi.javametadata.scraper.BaseScraper;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;
import java.util.regex.Pattern;

/** Scraper for Microsoft OpenJDK builds */
public class MicrosoftScraper extends BaseScraper {
    private static final String VENDOR = "microsoft";
    private static final Pattern FILENAME_PATTERN =
            Pattern.compile(
                    "microsoft-jdk-([0-9.]+)-(linux|macos|macOS|windows)-(x64|aarch64)\\.(tar\\.gz|zip|msi|dmg|pkg)$");

    public MicrosoftScraper(Path metadataDir, Path checksumDir, Logger logger) {
        super(metadataDir, checksumDir, logger);
    }

    @Override
    public String getScraperId() {
        return "microsoft";
    }

    @Override
    public String getVendorName() {
        return VENDOR;
    }

    @Override
    protected List<JdkMetadata> scrape() throws Exception {
        var allMetadata = new ArrayList<JdkMetadata>();

        // Download the Microsoft JDK download page
        var indexUrl = "https://docs.microsoft.com/en-us/java/openjdk/download";
        log("Fetching index from " + indexUrl);
        var html = httpUtils.downloadString(indexUrl);

        // Extract download links using regex
        var linkPattern =
                Pattern.compile(
                        "<a href=\"https://aka\\.ms/download-jdk/(microsoft-jdk-.+-(linux|macos|macOS|windows)-(x64|aarch64)\\.(tar\\.gz|zip|msi|dmg|pkg))\"");
        var matcher = linkPattern.matcher(html);

        var files = new ArrayList<String>();
        while (matcher.find()) {
            files.add(matcher.group(1));
        }

        log("Found " + files.size() + " files to process");

        for (var filename : files) {
            if (metadataExists(filename)) {
                log("Skipping " + filename + " (already exists)");
                continue;
            }

            var metadata = processFile(filename);
            if (metadata != null) {
                allMetadata.add(metadata);
            }
        }

        return allMetadata;
    }

    private JdkMetadata processFile(String filename) throws Exception {
        var matcher = FILENAME_PATTERN.matcher(filename);
        if (!matcher.matches()) {
            log("Filename doesn't match pattern: " + filename);
            return null;
        }

        var version = matcher.group(1);
        var os = matcher.group(2);
        var arch = matcher.group(3);
        var extension = matcher.group(4);

        var url = "https://aka.ms/download-jdk/" + filename;

        // Determine release type (aarch64 is EA for Microsoft)
        var releaseType = arch.equals("aarch64") ? "ea" : "ga";

        // Download file and compute hashes
        var download = downloadFile(url, filename);

        // Create metadata
        var metadata = new JdkMetadata();
        metadata.setVendor(VENDOR);
        metadata.setFilename(filename);
        metadata.setReleaseType(normalizeReleaseType(releaseType));
        metadata.setVersion(version);
        metadata.setJavaVersion(version);
        metadata.setJvmImpl("hotspot");
        metadata.setOs(normalizeOs(os));
        metadata.setArchitecture(normalizeArch(arch));
        metadata.setFileType(extension);
        metadata.setImageType("jdk");
        metadata.setFeatures(new ArrayList<>());
        metadata.setUrl(url);
        metadata.setMd5(download.md5());
        metadata.setMd5File(filename + ".md5");
        metadata.setSha1(download.sha1());
        metadata.setSha1File(filename + ".sha1");
        metadata.setSha256(download.sha256());
        metadata.setSha256File(filename + ".sha256");
        metadata.setSha512(download.sha512());
        metadata.setSha512File(filename + ".sha512");
        metadata.setSize(download.size());

        saveMetadataFile(metadata);
        log("Processed " + filename);

        return metadata;
    }
}
