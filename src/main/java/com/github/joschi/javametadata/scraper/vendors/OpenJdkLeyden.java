package com.github.joschi.javametadata.scraper.vendors;

import com.github.joschi.javametadata.scraper.Scraper;
import java.nio.file.Path;
import java.util.*;
import java.util.logging.Logger;

/** Scraper for OpenJDK Project Leyden early access builds */
public class OpenJdkLeyden extends OpenJdkBaseScraper {
    
    public OpenJdkLeyden(Path metadataDir, Path checksumDir, Logger logger) {
        super(metadataDir, checksumDir, logger);
    }


    @Override
    protected List<String> getIndexUrls() {
        return Collections.singletonList("http://jdk.java.net/leyden/");
    }

    @Override
    protected String getFeature() {
        return "leyden";
    }

    public static class Discovery implements Scraper.Discovery {
        @Override
        public String name() {
            return "openjdk-leyden";
        }

        @Override
        public String vendor() {
            return "openjdk";
        }

        @Override
        public Scraper create(Path metadataDir, Path checksumDir, Logger logger) {
            return new OpenJdkLeyden(metadataDir, checksumDir, logger);
        }
    }
}
