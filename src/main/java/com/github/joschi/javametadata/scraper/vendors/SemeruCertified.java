package com.github.joschi.javametadata.scraper.vendors;

import com.github.joschi.javametadata.scraper.Scraper;
import java.nio.file.Path;
import java.util.List;
import java.util.logging.Logger;

/** Scraper for IBM Semeru Certified Edition releases */
public class SemeruCertified extends SemeruBaseScraper {
    private static final String VENDOR = "semeru-certified";
    
    // List of Java versions for certified edition
    private static final List<String> JAVA_VERSIONS =
            List.of("11-certified", "17-certified", "21-certified", "25-certified");

    public SemeruCertified(Path metadataDir, Path checksumDir, Logger logger) {
        super(metadataDir, checksumDir, logger);
    }

    @Override
    protected List<String> getJavaVersions() {
        return JAVA_VERSIONS;
    }

    @Override
    protected String getFilenamePrefix() {
        return "ibm-semeru-certified-";
    }

    @Override
    protected String getVendor() {
        return VENDOR;
    }

    @Override
    protected List<String> getAdditionalFeatures() {
        return List.of("certified");
    }

    public static class Discovery implements Scraper.Discovery {
        @Override
        public String name() {
            return "semeru-certified";
        }

        @Override
        public String vendor() {
            return "IBM Semeru Certified";
        }

        @Override
        public Scraper create(Path metadataDir, Path checksumDir, Logger logger) {
            return new SemeruCertified(metadataDir, checksumDir, logger);
        }
    }
}
