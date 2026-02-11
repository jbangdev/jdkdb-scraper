# JDK Metadata DB Scraper - AI Coding Guide

## Project Overview
A parallel Java application that scrapes JDK metadata from 35+ vendors (Temurin, Zulu, Liberica, Corretto, etc.) via vendor APIs and GitHub releases. Outputs structured JSON metadata files with checksums for each JDK distribution.

## Architecture

### Core Execution Flow
1. **Main** (`Main.java`) - CLI entry via Picocli, manages ExecutorService for parallel scraping
2. **ScraperFactory** - Uses Java ServiceLoader to discover scrapers via `META-INF/services/dev.jbang.jdkdb.scraper.Scraper$Discovery`
3. **ProgressReporter** - Dedicated thread receives events from all scrapers via `BlockingQueue<ProgressEvent>`
4. **Scrapers** - Each vendor scraper implements `Callable<ScraperResult>` for concurrent execution

### Base Class Hierarchy
- **BaseScraper** - Common functionality: HTTP downloads, hash computation, metadata persistence, progress tracking
- **GitHubReleaseScraper** extends BaseScraper - GitHub API integration with pagination and rate limiting
- **AdoptiumMarketplaceScraper** extends BaseScraper - Adoptium Marketplace API patterns
- Vendor scrapers (e.g., `Temurin`, `Microsoft`, `SemeruBaseScraper`) - Specific API implementations

### Service Provider Interface (SPI)
All scrapers register via nested `Discovery` class implementing `Scraper.Discovery`:
```java
public static class Discovery implements Scraper.Discovery {
    public String name() { return "vendor-id"; }
    public String vendor() { return "vendor-name"; }
    public When when() { return When.ALWAYS; }
    public Scraper create(ScraperConfig config) { return new VendorScraper(config); }
}
```
Registration: Add fully qualified class name to `src/main/resources/META-INF/services/dev.jbang.jdkdb.scraper.Scraper$Discovery`

## Critical Developer Workflows

### Building and Formatting
```bash
./gradlew spotlessApply build   # ALWAYS run spotlessApply before build
./gradlew standaloneJar         # Creates fat jar with dependencies
```
**Never commit without running `spotlessApply`** - enforces Palantir Java Format, tabs (not spaces), UNIX line endings.

### Running Scrapers
```bash
java -jar build/libs/jdkdb-scraper-*-standalone.jar --list              # List all scrapers
java -jar build/libs/jdkdb-scraper-*-standalone.jar --scrapers temurin  # Run specific vendor
java -jar build/libs/jdkdb-scraper-*-standalone.jar --from-start        # Ignore existing metadata
java -jar build/libs/jdkdb-scraper-*-standalone.jar --limit-progress 3  # Limit to 3 items for testing
```

### GitHub Token for Rate Limiting
Set `GITHUB_TOKEN` environment variable to avoid GitHub API rate limits when scraping GitHub-based vendors.

## Project-Specific Conventions

### Metadata Processing Pattern
**Critical**: Scrapers must distinguish between already-processed items (skip) vs. new items (download):
```java
protected List<JdkMetadata> scrape() throws Exception {
    for (Item item : items) {
        if (metadataExists(filename)) {
            allMetadata.add(skipped(filename));  // Track but don't re-download
            continue;
        }
        // Download, compute hashes, save
        success(filename);  // Report progress
    }
}
```

### Exception Handling Strategy
- **InterruptedProgressException** / **TooManyFailuresException** - Rethrow immediately, stops scraper
- **Other exceptions** - Call `fail(message, exception)` to log but continue processing other items
- **Never catch** these special exceptions in item loops

### Progress Tracking Methods
Inherited from BaseScraper:
- `success(filename)` - Increment processed count, report success
- `skip(filename)` - Increment skipped count, report skip
- `fail(message, exception)` - Increment failure count, check against `maxFailureCount`
- `log(message)`, `warn(message)`, `fine(message)` - Logging at different levels

### OS & Architecture Normalization
Use inherited normalization methods from BaseScraper:
- `normalizeOs(os)` - Maps "mac"/"darwin"→"macosx", "win"→"windows", etc.
- `normalizeArch(arch)` - Maps "amd64"/"x64"→"x86_64", "arm64"→"aarch64", etc.
- See tests in `BaseScraperTest.java` for complete mappings

## Data Model

### JdkMetadata Fields (snake_case in JSON)
Required: `vendor`, `filename`, `version`, `java_version`, `os`, `architecture`, `file_type`, `image_type`, `url`
Checksums: `md5`, `sha1`, `sha256`, `sha512` + corresponding `*_file` fields for external checksum URLs
Features: Array of strings (e.g., `["openj9"]`, `["lts"]`, `["musl"]`)
This class follow the API defined in `./openapi.yaml` and can't be changed!

### Output Structure
```
docs/
├── metadata/vendor/{vendor-name}/*.json  # Individual release metadata
└── checksums/vendor/{vendor-name}/*      # Hash files
```

## Testing

### Test Structure
- Unit tests in `src/test/java/dev/jbang/jdkdb/scraper/`
- Use AssertJ for assertions: `assertThat(actual).isEqualTo(expected)`
- Mock scrapers extend BaseScraper with dummy implementations

### Running Tests
```bash
./gradlew test
./gradlew test --tests BaseScraperTest
```

## Common Patterns

### GitHub Pagination (GitHubReleaseScraper)
```java
for (String repo : getGitHubRepos()) {
    int page = 1;
    while (true) {
        String releasesUrl = String.format("%s/%s/%s/releases?page=%d", GITHUB_API_BASE, org, repo, page);
        JsonNode releases = fetchJson(releasesUrl);
        if (releases.size() == 0) break;
        processReleases(releases);
        page++;
    }
}
```

### Adoptium Marketplace Pattern
Override `getApiBase()`, `getAvailableReleasesPath()`, `getAssetsPathTemplate()`, and process binaries per release.

## Dependencies
- Jackson 2.16.1 for JSON (use `readJson(string)` helper in BaseScraper)
- Java 21+ HttpClient (`java.net.http`) - configured in `HttpUtils` with 30s timeout, auto-redirect
- SLF4J/Logback - Logger per scraper: `LoggerFactory.getLogger("vendors." + name)`
- Picocli 4.7.5 - CLI in Main.java only

## Key Files to Reference
- [BaseScraper.java](src/main/java/dev/jbang/jdkdb/scraper/BaseScraper.java) - All helper methods and patterns
- [Temurin.java](src/main/java/dev/jbang/jdkdb/scraper/vendors/Temurin.java) - Adoptium Marketplace example
- [SemeruBaseScraper.java](src/main/java/dev/jbang/jdkdb/scraper/vendors/SemeruBaseScraper.java) - GitHub Release + repo discovery
- [BaseScraperTest.java](src/test/java/dev/jbang/jdkdb/scraper/BaseScraperTest.java) - OS/arch normalization reference
