package com.github.joschi.javametadata.scraper.vendors;

import com.github.joschi.javametadata.scraper.Scraper;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

/** Scraper for IBM Semeru Open Edition releases */
public class Semeru extends SemeruBaseScraper {
    private static final String VENDOR = "semeru";
    
    // List of Java versions for open edition
    private static final List<String> JAVA_VERSIONS =
            List.of("8", "11", "16", "17", "18", "19", "20", "21", "22", "23", "24", "25");

    public Semeru(Path metadataDir, Path checksumDir, Logger logger) {
        super(metadataDir, checksumDir, logger);
    }

    @Override
    protected List<String> getJavaVersions() {
        return JAVA_VERSIONS;
    }

    @Override
    protected String getFilenamePrefix() {
        return "ibm-semeru-open-";
    }

    @Override
    protected String getVendor() {
        return VENDOR;
    }

    @Override
    protected List<String> getAdditionalFeatures() {
        return new ArrayList<>();
    }

    public static class Discovery implements Scraper.Discovery {
        @Override
        public String name() {
            return "semeru";
        }

        @Override
        public String vendor() {
            return "semeru";
        }

        @Override
        public Scraper create(Path metadataDir, Path checksumDir, Logger logger) {
            return new Semeru(metadataDir, checksumDir, logger);
        }
    }

}
