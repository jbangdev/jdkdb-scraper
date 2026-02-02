package com.github.joschi.javametadata.scraper.vendors;

import com.github.joschi.javametadata.scraper.Scraper;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Logger;

/** Scraper for mainline OpenJDK releases */
public class OpenJdk extends OpenJdkBaseScraper {
    public OpenJdk(Path metadataDir, Path checksumDir, Logger logger) {
        super(metadataDir, checksumDir, logger);
    }


    @Override
    protected List<String> getIndexUrls() {
        return Arrays.asList(
                "http://jdk.java.net/archive/",
                "http://jdk.java.net/21/",
                "http://jdk.java.net/22/",
                "http://jdk.java.net/23/",
                "http://jdk.java.net/24/",
                "http://jdk.java.net/25/",
                "http://jdk.java.net/26/");
    }

    public static class Discovery implements Scraper.Discovery {
        @Override
        public String name() {
            return "openjdk";
        }

        @Override
        public String vendor() {
            return "openjdk";
        }

        @Override
        public Scraper create(Path metadataDir, Path checksumDir, Logger logger) {
            return new OpenJdk(metadataDir, checksumDir, logger);
        }
    }
}
