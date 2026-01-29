# Java Metadata Scraper

A Java-based application for scraping JDK metadata from various vendors. This project replaces the original bash scripts with a robust, parallel Java implementation.

## Features

- **Parallel Execution**: Run multiple vendor scrapers concurrently for improved performance
- **Selective Scraping**: Run all scrapers or select specific vendors
- **Central Reporting**: Thread-safe progress reporting with real-time status updates
- **Extensible Architecture**: Easy to add new vendor scrapers
- **Generic Base Classes**: Reduces code duplication for similar vendors (e.g., Semeru versions)
- **Comprehensive Logging**: SLF4J/Logback integration with both console and file output

## Prerequisites

- Java 21 or higher

## Building

This project uses Maven for dependency management and building.

```bash
# Build the project
./mvnw clean package

# This creates two jars:
# - java-metadata-1.0.0-SNAPSHOT.jar (regular jar)
# - java-metadata-1.0.0-SNAPSHOT-standalone.jar (fat jar with all dependencies)
```

## Usage

### Running using the standalone JAR:

```bash
# Run all scrapers
java -jar build/libs/java-metadata-scraper-1.0.0-SNAPSHOT-standalone.jar

# List available scrapers
java -jar build/libs/java-metadata-scraper-1.0.0-SNAPSHOT-standalone.jar --list

# Run specific scrapers
java -jar build/libs/java-metadata-scraper-1.0.0-SNAPSHOT-standalone.jar --scrapers microsoft,semeru-11,semeru-17

# Specify custom directories
java -jar build/libs/java-metadata-scraper-1.0.0-SNAPSHOT-standalone.jar \
  --metadata-dir /path/to/metadata \
  --checksum-dir /path/to/checksums

# Control parallelism
java -jar build/libs/java-metadata-scraper-1.0.0-SNAPSHOT-standalone.jar --threads 4

# Show help
java -jar build/libs/java-metadata-scraper-1.0.0-SNAPSHOT-standalone.jar --help
```

## Command Line Options

```
Usage: java-metadata-scraper [-hlV] [-c=<checksumDir>] [-m=<metadataDir>]
                              [-s=<scraperIds>[,<scraperIds>...]]...
                              [-t=<maxThreads>]

Scrapes JDK metadata from various vendors

Options:
  -m, --metadata-dir=<metadataDir>
                        Directory to store metadata files (default: docs/metadata)
  -c, --checksum-dir=<checksumDir>
                        Directory to store checksum files (default: docs/checksums)
  -s, --scrapers=<scraperIds>[,<scraperIds>...]
                        Comma-separated list of scraper IDs to run (if not specified,
                        all scrapers run)
  -l, --list            List all available scraper IDs and exit
  -t, --threads=<maxThreads>
                        Maximum number of parallel scraper threads (default: number
                        of processors)
  -h, --help            Show this help message and exit.
  -V, --version         Print version information and exit.
```

## Architecture

### Core Components

- **ProgressReporter**: Central reporting thread that receives and logs progress events from all scrapers
- **BaseScraper**: Abstract base class for all scrapers with common functionality (downloading, hashing, metadata saving)
- **GitHubReleaseScraper**: Specialized base class for scrapers that fetch releases from GitHub
- **ScraperFactory**: Factory class for instantiating scrapers

### Vendor Scrapers

Current implementations:
- **MicrosoftScraper**: Scrapes Microsoft OpenJDK builds
- **SemeruBaseScraper**: Base class for IBM Semeru scrapers
  - Semeru8Scraper, Semeru11Scraper, Semeru17Scraper, Semeru21Scraper

### Adding New Scrapers

1. Create a new class extending `BaseScraper` or `GitHubReleaseScraper`
2. Implement required abstract methods
3. Add the scraper to `ScraperFactory.createAllScrapers()`

Example:

```java
public class NewVendorScraper extends BaseScraper {
    public NewVendorScraper(Path metadataDir, Path checksumDir, ProgressReporter reporter) {
        super(metadataDir.resolve("vendor-name"), checksumDir.resolve("vendor-name"), reporter);
    }

    @Override
    public String getScraperId() {
        return "vendor-name";
    }

    @Override
    public String getVendorName() {
        return "vendor-name";
    }

    @Override
    protected List<JdkMetadata> scrape() throws Exception {
        // Implementation here
    }
}
```

## Project Structure

```
src/
├── main/
│   ├── java/
│   │   └── com/github/joschi/javametadata/
│   │       ├── Main.java                          # CLI application entry point
│   │       ├── model/
│   │       │   └── JdkMetadata.java              # Data model for JDK metadata
│   │       ├── reporting/
│   │       │   ├── ProgressEvent.java            # Progress event types
│   │       │   └── ProgressReporter.java         # Central reporting thread
│   │       ├── scraper/
│   │       │   ├── BaseScraper.java              # Base class for all scrapers
│   │       │   ├── GitHubReleaseScraper.java     # Base for GitHub-based scrapers
│   │       │   ├── ScraperFactory.java           # Factory for creating scrapers
│   │       │   ├── ScraperResult.java            # Result wrapper
│   │       │   └── vendors/
│   │       │       ├── MicrosoftScraper.java
│   │       │       ├── SemeruBaseScraper.java
│   │       │       ├── Semeru8Scraper.java
│   │       │       ├── Semeru11Scraper.java
│   │       │       ├── Semeru17Scraper.java
│   │       │       └── Semeru21Scraper.java
│   │       └── util/
│   │           ├── FileUtils.java                # File operations
│   │           ├── HashUtils.java                # Hash computation
│   │           └── HttpUtils.java                # HTTP operations
│   └── resources/
│       └── logback.xml                           # Logging configuration
└── test/
    └── java/
        └── (test classes)
```

## Dependencies

- **Jackson**: JSON processing
- **Apache HttpClient 5**: HTTP operations
- **JSoup**: HTML parsing
- **SLF4J/Logback**: Logging
- **Picocli**: Command-line interface
- **JUnit 5**: Testing

## Output

The scrapers generate two types of output:

1. **Metadata files**: JSON files containing JDK metadata (stored in `docs/metadata/<vendor>/`)
2. **Checksum files**: MD5, SHA1, SHA256, SHA512 checksums (stored in `docs/checksums/<vendor>/`)

Each vendor directory contains:
- Individual `.json` files for each JDK release
- An `all.json` file combining all releases for that vendor

## Logging

Logs are written to:
- Console (STDOUT) - Real-time progress
- File (`logs/java-metadata-scraper.log`) - Detailed execution log

The logging configuration can be customized in `src/main/resources/logback.xml`.

## Requirements

- Java 11 or higher
- Maven 3.6+

## License

Same as the original project (see LICENSE file)
