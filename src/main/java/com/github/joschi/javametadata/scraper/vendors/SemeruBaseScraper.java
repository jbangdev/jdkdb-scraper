package com.github.joschi.javametadata.scraper.vendors;

import com.fasterxml.jackson.databind.JsonNode;
import com.github.joschi.javametadata.model.JdkMetadata;
import com.github.joschi.javametadata.scraper.GitHubReleaseScraper;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Base scraper for IBM Semeru releases */
public abstract class SemeruBaseScraper extends GitHubReleaseScraper {
    private static final String VENDOR = "semeru";
    private static final String ORG = "ibmruntimes";

    public SemeruBaseScraper(Path metadataDir, Path checksumDir, Logger logger) {
        super(metadataDir, checksumDir, logger);
    }

    @Override
    protected String getGitHubOrg() {
        return ORG;
    }

    @Override
    public String getVendorName() {
        return VENDOR;
    }

    /** Get the Java version this scraper handles */
    protected abstract String getJavaVersion();

    @Override
    protected String getGitHubRepo() {
        return "semeru" + getJavaVersion() + "-binaries";
    }

    @Override
    public String getScraperId() {
        return "semeru-" + getJavaVersion();
    }

    @Override
    protected List<JdkMetadata> processRelease(JsonNode release) throws Exception {
        var metadataList = new ArrayList<JdkMetadata>();

        var tagName = release.get("tag_name").asText();

        // Parse version from tag name
        var versionPattern = Pattern.compile("jdk-(.*)_openj9-(.*)");
        var versionMatcher = versionPattern.matcher(tagName);

        if (!versionMatcher.matches()) {
            return metadataList;
        }

        var javaVersion = versionMatcher.group(1);
        var openj9Version = versionMatcher.group(2);
        var version = javaVersion + "_openj9-" + openj9Version;

        var assets = release.get("assets");
        if (assets == null || !assets.isArray()) {
            return metadataList;
        }

        for (var asset : assets) {
            var assetName = asset.get("name").asText();
            var downloadUrl = asset.get("browser_download_url").asText();

            // Skip non-JDK/JRE files
            if (!assetName.startsWith("ibm-semeru-open-")) {
                continue;
            }

            if (metadataExists(assetName)) {
                log("Skipping " + assetName + " (already exists)");
                continue;
            }

            JdkMetadata metadata = processAsset(assetName, downloadUrl, version, javaVersion);
            if (metadata != null) {
                metadataList.add(metadata);
            }
        }

        return metadataList;
    }

    private JdkMetadata processAsset(
            String filename, String url, String version, String javaVersion) throws Exception {

        // Parse filename
        Pattern rpmPattern =
                Pattern.compile(
                        "ibm-semeru-open-[0-9]+-(jre|jdk)-(.+)\\.(x86_64|s390x|ppc64|ppc64le|aarch64)\\.rpm$");
        Pattern tarPattern =
                Pattern.compile(
                        "ibm-semeru-open-(jre|jdk)_(x64|x86-32|s390x|ppc64|ppc64le|aarch64)_(aix|linux|mac|windows)_.+_openj9-.+\\.(tar\\.gz|zip|msi)$");

        String imageType = null;
        String arch = null;
        String os = null;
        String extension = null;

        Matcher rpmMatcher = rpmPattern.matcher(filename);
        if (rpmMatcher.matches()) {
            imageType = rpmMatcher.group(1);
            arch = rpmMatcher.group(3);
            os = "linux";
            extension = "rpm";
        } else {
            Matcher tarMatcher = tarPattern.matcher(filename);
            if (tarMatcher.matches()) {
                imageType = tarMatcher.group(1);
                arch = tarMatcher.group(2);
                os = tarMatcher.group(3);
                extension = tarMatcher.group(4);
            }
        }

        if (imageType == null) {
            log("Could not parse filename: " + filename);
            return null;
        }

        // Download and compute hashes
        var download = downloadFile(url, filename);

        // Create metadata
        var metadata = new JdkMetadata();
        metadata.setVendor(VENDOR);
        metadata.setFilename(filename);
        metadata.setReleaseType("ga");
        metadata.setVersion(version);
        metadata.setJavaVersion(javaVersion);
        metadata.setJvmImpl("openj9");
        metadata.setOs(normalizeOs(os));
        metadata.setArchitecture(normalizeArch(arch));
        metadata.setFileType(extension);
        metadata.setImageType(imageType);
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
