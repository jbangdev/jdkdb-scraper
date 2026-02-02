package com.github.joschi.javametadata.scraper.vendors;

import com.github.joschi.javametadata.scraper.Scraper;
import java.nio.file.Path;
import java.util.*;
import java.util.logging.Logger;

/** Scraper for OpenJDK Project Valhalla early access builds */
public class OpenJdkValhalla extends OpenJdkBaseScraper {
    
    public OpenJdkValhalla(Path metadataDir, Path checksumDir, Logger logger) {
        super(metadataDir, checksumDir, logger);
    }


    @Override
    protected List<String> getIndexUrls() {
        return Collections.singletonList("http://jdk.java.net/valhalla/");
    }

    @Override
    protected String getFeature() {
        return "valhalla";
    }

    public static class Discovery implements Scraper.Discovery {
        @Override
        public String name() {
            return "openjdk-valhalla";
        }

        @Override
        public String vendor() {
            return "openjdk";
        }

        @Override
        public Scraper create(Path metadataDir, Path checksumDir, Logger logger) {
            return new OpenJdkValhalla(metadataDir, checksumDir, logger);
        }
    }
}
