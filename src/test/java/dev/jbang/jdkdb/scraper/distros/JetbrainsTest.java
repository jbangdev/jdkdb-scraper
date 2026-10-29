package dev.jbang.jdkdb.scraper.distros;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.jbang.jdkdb.model.JdkMetadata;
import dev.jbang.jdkdb.scraper.ScraperConfig;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.slf4j.LoggerFactory;

class JetbrainsTest {
	@TempDir
	Path tempDir;

	@ParameterizedTest
	@CsvSource({
		"jbrsdk-21.0.11-windows-x64-fastdebug-b1163.125.zip, Windows SDK, fastdebug",
		"jbrsdk-21.0.11-windows-x64-fastdebug-b1163.125.tar.gz, fastdebug, fastdebug",
		"jbrsdk_jcef-21.0.11-windows-x64-b1163.125.zip, Windows SDK, jcef",
		"jbr_jcef-21.0.11-windows-x64-b1163.125.zip, JCEF, jcef",
		"jbrsdk_ft-21.0.11-osx-aarch64-b1163.125.tar.gz, SDK, freetype",
		"jbrsdk-21.0.11-windows-x64-b1163.125_pdb.zip, Windows SDK, debug",
		"jbrsdk_jcef-21.0.11-windows-x64-fastdebug-b1163.125_pdb.zip, SDK, fastdebug;debug;jcef",
		"jbrsdk-21.0.11-windows-x64-b1163.125.zip, Windows SDK, ''",
		"jbrsdk-21.0.11-windows-x64-b1163.125.zip, fastdebug debug symbols FreeType JCEF Legacy Binary, fastdebug;debug;freetype;jcef;legacy",
		"jbrsdk-21.0.11-linux-musl-x64-b1163.125.tar.gz, SDK, musl"
	})
	void detectsFeaturesFromFilenamesAndDescriptions(String filename, String description, String expectedFeatures)
			throws Exception {
		List<JdkMetadata> assets = new ArrayList<>();
		ScraperConfig config = new ScraperConfig(
				tempDir, tempDir, LoggerFactory.getLogger(JetbrainsTest.class), false, 0, 0, Duration.ZERO, md -> {});
		Jetbrains scraper = new Jetbrains(config) {
			@Override
			protected void process(JdkMetadata metadata) {
				assets.add(metadata);
			}
		};
		String url = "https://example.com/" + filename;
		var release = new ObjectMapper().createObjectNode();
		release.put("prerelease", false);
		release.put(
				"body", "| " + description + " | [" + filename + "](" + url + ") | [checksum](" + url + ".checksum) |");

		scraper.processRelease(release);

		assertThat(assets).hasSize(1);
		assertThat(assets.getFirst().getFeatures())
				.containsExactly(expectedFeatures.isEmpty() ? new String[0] : expectedFeatures.split(";"));
	}
}
