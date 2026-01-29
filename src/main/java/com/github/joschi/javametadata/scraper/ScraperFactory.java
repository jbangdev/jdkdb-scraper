package com.github.joschi.javametadata.scraper;

import com.github.joschi.javametadata.reporting.ProgressReporter;
import com.github.joschi.javametadata.reporting.ProgressReporterLogger;
import com.github.joschi.javametadata.scraper.vendors.*;
import java.nio.file.Path;
import java.util.*;

/** Factory for creating scraper instances */
public class ScraperFactory {

    /** Create all available scrapers */
    public static List<Scraper> createAllScrapers(
            Path metadataDir, Path checksumDir, ProgressReporter reporter) {
        List<Scraper> scrapers = new ArrayList<>();

        Path metadataVendorDir = metadataDir.resolve("vendor");

        // Microsoft
        scrapers.add(
                new MicrosoftScraper(
                        metadataVendorDir.resolve("microsoft"),
                        checksumDir.resolve("microsoft"),
                        ProgressReporterLogger.forScraper("microsoft", reporter)));

        // Semeru versions
        scrapers.add(
                new Semeru8Scraper(
                        metadataVendorDir.resolve("semeru"),
                        checksumDir.resolve("semeru"),
                        ProgressReporterLogger.forScraper("semeru-8", reporter)));
        scrapers.add(
                new Semeru11Scraper(
                        metadataVendorDir.resolve("semeru"),
                        checksumDir.resolve("semeru"),
                        ProgressReporterLogger.forScraper("semeru-11", reporter)));
        scrapers.add(
                new Semeru17Scraper(
                        metadataVendorDir.resolve("semeru"),
                        checksumDir.resolve("semeru"),
                        ProgressReporterLogger.forScraper("semeru-17", reporter)));
        scrapers.add(
                new Semeru21Scraper(
                        metadataVendorDir.resolve("semeru"),
                        checksumDir.resolve("semeru"),
                        ProgressReporterLogger.forScraper("semeru-21", reporter)));

        // Add more scrapers here as they are implemented

        return scrapers;
    }

    /** Create specific scrapers by ID */
    public static List<Scraper> createScrapers(
            List<String> scraperIds,
            Path metadataDir,
            Path checksumDir,
            ProgressReporter reporter) {
        Map<String, Scraper> allScrapers = new HashMap<>();

        // Build map of all available scrapers
        for (Scraper scraper : createAllScrapers(metadataDir, checksumDir, reporter)) {
            allScrapers.put(scraper.getScraperId(), scraper);
        }

        // Select requested scrapers
        List<Scraper> selectedScrapers = new ArrayList<>();
        for (String id : scraperIds) {
            Scraper scraper = allScrapers.get(id);
            if (scraper != null) {
                selectedScrapers.add(scraper);
            } else {
                throw new IllegalArgumentException("Unknown scraper ID: " + id);
            }
        }

        return selectedScrapers;
    }

    /** Get all available scraper IDs */
    public static List<String> getAvailableScraperIds(
            Path metadataDir, Path checksumDir, ProgressReporter reporter) {
        List<String> ids = new ArrayList<>();
        for (Scraper scraper : createAllScrapers(metadataDir, checksumDir, reporter)) {
            ids.add(scraper.getScraperId());
        }
        Collections.sort(ids);
        return ids;
    }
}
