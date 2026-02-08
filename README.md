# jdkdb-scraper - JDK Metadata DB Scraper

A Java-based application for scraping JDK metadata from various vendors. This project replaces the original bash scripts with a robust, parallel Java implementation.

This project is based on [Joschi's Java Metadata project](https://github.com/joschi/java-metadata) and incorporates ideas from the [Foojay's Disco API project](https://github.com/foojayio/discoapi).

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

This project uses Gradle for dependency management and building.

```bash
# Build the project
./gradlew spotlessApply build

# This creates two jars:
# - jdkdb-scraper-1.0.0-SNAPSHOT.jar (regular jar)
# - jdkdb-scraper-1.0.0-SNAPSHOT-standalone.jar (fat jar with all dependencies)
```

## Usage

### Running using the standalone JAR:

```bash
# Run all scrapers
java -jar build/libs/jdkdb-scraper-1.0.0-SNAPSHOT-standalone.jar

# List available scrapers
java -jar build/libs/jdkdb-scraper-1.0.0-SNAPSHOT-standalone.jar --list

# Run specific scrapers
java -jar build/libs/jdkdb-scraper-1.0.0-SNAPSHOT-standalone.jar --scrapers microsoft,semeru-11,semeru-17

# Specify custom directories
java -jar build/libs/jdkdb-scraper-1.0.0-SNAPSHOT-standalone.jar \
--metadata-dir /path/to/metadata \
--checksum-dir /path/to/checksums

# Control parallelism
java -jar build/libs/jdkdb-scraper-1.0.0-SNAPSHOT-standalone.jar --threads 4

# Show help
java -jar build/libs/jdkdb-scraper-1.0.0-SNAPSHOT-standalone.jar --help
```

## Command Line Options

```
Usage: jdkdb-scraper [-hlV] [--from-start] [-c=<checksumDir>]
							[-m=<metadataDir>] [--limit-progress=<limitProgress>]
							[--max-failures=<maxFailures>]
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
	--from-start      Ignore existing metadata files and scrape all items from the
						start
	--limit-progress=<limitProgress>
						Maximum number of metadata items to process per scraper
						before aborting (default: unlimited)
	--max-failures=<maxFailures>
						Maximum number of allowed failures per scraper before
						aborting that scraper (default: 10)
-h, --help            Show this help message and exit.
-V, --version         Print version information and exit.
```

## Architecture

### Core Components

- **ProgressReporter**: Central reporting thread that receives and logs progress events from all scrapers
- **ScraperConfig**: Configuration record encapsulating metadata directory, checksum directory, logger, etc
- **BaseScraper**: Abstract base class for all scrapers with common functionality (downloading, hashing, metadata saving)
- **GitHubReleaseScraper**: Specialized base class for scrapers that fetch releases from GitHub
- **AdoptiumMarketplaceScraper**: Specialized base class for scrapers using Adoptium Marketplace API
- **ScraperFactory**: Factory class that uses ServiceLoader to dynamically discover and instantiate scrapers
- **Scraper.Discovery**: Service provider interface for scraper registration via Java ServiceLoader

### Vendor Scrapers

The project includes 35+ vendor scrapers, supporting all major JDK distributions:

- **Temurin** (Eclipse Adoptium): temurin, temurin-ea
- **Zulu** (Azul): zulu, zulu-prime
- **Liberica** (BellSoft): liberica, liberica-native
- **Corretto** (Amazon)
- **SapMachine** (SAP)
- **Microsoft** (Microsoft Build of OpenJDK)
- **OpenJDK**: openjdk, openjdk-leyden, openjdk-loom, openjdk-valhalla
- **Dragonwell** (Alibaba)
- **Kona** (Tencent)
- **Oracle**: oracle, oracle-graalvm, oracle-graalvm-ea
- **Semeru** (IBM): semeru, semeru-certified
- **Trava** (TravaOpenJDK)
- **AdoptOpenJDK** (Legacy)
- **Bisheng** (Huawei)
- **Red Hat**
- **GraalVM**: graalvm-legacy, graalvm-ce, graalvm-ce-ea, graalvm-community, graalvm-community-ea
- **IBM JDK**
- **Java SE RI** (Reference Implementation)
- **JetBrains Runtime**
- **Mandrel** (Red Hat's GraalVM)
- **Gluon GraalVM**
- **OpenLogic**

Each scraper is registered via Java's ServiceLoader mechanism in `META-INF/services`.

### Adding New Scrapers

1. Create a new class extending `BaseScraper`, `GitHubReleaseScraper`, or `AdoptiumMarketplaceScraper`
2. Implement required abstract methods
3. Add an inner `Discovery` class implementing `Scraper.Discovery`
4. Register the discovery class in `META-INF/services/dev.jbang.jdkdb.scraper.Scraper$Discovery`

Example:

```java
public class NewScraper extends BaseScraper {
	public NewScraper(ScraperConfig config) {
		super(config);
	}

	@Override
	protected List<JdkMetadata> scrape() throws Exception {
		List<JdkMetadata> allMetadata = new ArrayList<>();

		try {
			// Figure out what items to scrape here...
			var allItems = ...;
			for (item in allItems) {
				try {
					JdkMetadata metadata = scrapeItem(item);
					allMetadata.add(metadata);
				} catch (InterruptedProgressException|TooManyFailuresException e) {
					// We don't want these exceptions to mark the item as failed
					throw e;
				} catch (Exception e) {
					// For recoverable problems we call fail():
					fail(filename, e);
				}
			}
		} catch (InterruptedProgressException ex) {
			// When we receive this exception we stop scraping
			// but we return whatever is already processed
		}

		return allMetadata;
	}

	private void scrapeItem(...) throws Exception {
		// Here we scrape a single item
		JdkMetadata metadata = ...;
		// For each successful item created we call:
		saveMetadataFile(metadata);
		success(filename);
	}

	// ServiceLoader discovery
	public static class Discovery implements Scraper.Discovery {
		@Override
		public String name() {
			return "scraper-name";
		}

		@Override
		public String vendor() {
			return "vendor-name";
		}

		@Override
		public Scraper create(ScraperConfig config) {
			return new NewScraper(config);
		}
	}
}
```

## Project Structure

```
src/
├── main/
│   ├── java/
│   │   └── dev/jbang/jdkdb/
│   │       ├── Main.java                          # CLI application entry point
│   │       ├── model/
│   │       │   └── JdkMetadata.java              # Data model for JDK metadata
│   │       ├── reporting/
│   │       │   ├── ProgressEvent.java            # Progress event types
│   │       │   ├── ProgressReporter.java         # Central reporting thread
│   │       │   └── ProgressReporterLogger.java   # Logger adapter for scrapers
│   │       ├── scraper/
│   │       │   ├── Scraper.java                  # Scraper interface with Discovery SPI
│   │       │   ├── ScraperConfig.java            # Configuration record
│   │       │   ├── BaseScraper.java              # Base class for all scrapers
│   │       │   ├── GitHubReleaseScraper.java     # Base for GitHub-based scrapers
│   │       │   ├── AdoptiumMarketplaceScraper.java # Base for Adoptium Marketplace
│   │       │   ├── ScraperFactory.java           # Factory using ServiceLoader
│   │       │   ├── ScraperResult.java            # Result wrapper
│   │       │   └── vendors/
│   │       │       ├── TemurinScraper.java
│   │       │       ├── ZuluScraper.java
│   │       │       ├── LibericaScraper.java
│   │       │       ├── MicrosoftScraper.java
│   │       │       ├── SemeruScraper.java
│   │       │       ├── ... (35+ vendor scrapers)
│   │       │       └── (See full list in Vendor Scrapers section)
│   │       └── util/
│   │           ├── FileUtils.java                # File operations
│   │           ├── HashUtils.java                # Hash computation
│   │           └── HttpUtils.java                # HTTP operations
│   └── resources/
│       ├── logback.xml                           # Logging configuration
│       └── META-INF/
│           └── services/
│               └── dev.jbang.jdkdb.scraper.Scraper$Discovery
└── test/
	└── java/
		└── (test classes)
```

## Dependencies

- **Jackson**: JSON processing (2.16.1)
- **Java HttpClient** (`java.net.http`): HTTP operations (built-in)
- **SLF4J/Logback**: Logging (SLF4J 2.0.7, Logback 1.4.14)
- **Picocli**: Command-line interface (4.7.5)
- **JUnit 5**: Testing (5.10.1)

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
- File (`logs/jdkdb-scraper.log`) - Detailed execution log

The logging configuration can be customized in `src/main/resources/logback.xml`.

## Requirements

- Java 21 or higher
- Gradle 8.x (included via wrapper)

## License

Same as the original project (see LICENSE file)
