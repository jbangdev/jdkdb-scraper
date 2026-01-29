package com.github.joschi.javametadata.scraper.vendors;

import java.nio.file.Path;
import java.util.logging.Logger;

public class Semeru17Scraper extends SemeruBaseScraper {
    public Semeru17Scraper(Path metadataDir, Path checksumDir, Logger logger) {
        super(metadataDir, checksumDir, logger);
    }

    @Override
    protected String getJavaVersion() {
        return "17";
    }
}
