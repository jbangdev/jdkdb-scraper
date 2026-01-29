package com.github.joschi.javametadata.scraper.vendors;

import java.nio.file.Path;
import java.util.logging.Logger;

public class Semeru21Scraper extends SemeruBaseScraper {
    public Semeru21Scraper(Path metadataDir, Path checksumDir, Logger logger) {
        super(metadataDir, checksumDir, logger);
    }

    @Override
    protected String getJavaVersion() {
        return "21";
    }
}
