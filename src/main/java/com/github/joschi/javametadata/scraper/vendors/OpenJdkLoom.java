package com.github.joschi.javametadata.scraper.vendors;

import com.github.joschi.javametadata.scraper.Scraper;
import java.nio.file.Path;
import java.util.*;
import java.util.logging.Logger;

/** Scraper for OpenJDK Project Loom early access builds */
public class OpenJdkLoom extends OpenJdkBaseScraper {
    
    public OpenJdkLoom(Path metadataDir, Path checksumDir, Logger logger) {
        super(metadataDir, checksumDir, logger);
    }


    @Override
    protected List<String> getIndexUrls() {
        return Collections.singletonList("https://jdk.java.net/loom/");
    }

    @Override
    protected String getFeature() {
        return "loom";
    }

    public static class Discovery implements Scraper.Discovery {
        @Override
        public String name() {
            return "openjdk-loom";
        }

        @Override
        public String vendor() {
            return "openjdk";
        }

        @Override
        public Scraper create(Path metadataDir, Path checksumDir, Logger logger) {
            return new OpenJdkLoom(metadataDir, checksumDir, logger);
        }
    }
}
