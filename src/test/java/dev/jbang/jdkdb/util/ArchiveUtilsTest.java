package dev.jbang.jdkdb.util;

import static org.assertj.core.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Path;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

public class ArchiveUtilsTest {

	@TempDir
	Path tempDir;

	@Test
	@EnabledOnOs({OS.MAC})
	@Disabled("Large file download and extraction - only run manually when needed")
	public void testMacOsPkg() throws IOException, InterruptedException {
		Path pkgFile = tempDir.resolve("test-macosx.pkg");
		HttpUtils httpUtils = new HttpUtils();
		httpUtils.downloadFile(
				"https://github.com/ibmruntimes/semeru11-binaries/releases/download/jdk-11.0.29%2B7_openj9-0.56.0/ibm-semeru-open-jdk_aarch64_mac_11.0.29_7_openj9-0.56.0.pkg",
				pkgFile);
		var releaseInfo =
				ArchiveUtils.extractReleaseInfo(pkgFile, pkgFile.getFileName().toString());
		assertThat(releaseInfo).isNotNull();
	}

	@Test
	@Disabled("Large file download and extraction - only run manually when needed")
	public void testMacOsTarGz() throws IOException, InterruptedException {
		Path tarGzFile = tempDir.resolve("test-macosx.tar.gz");
		HttpUtils httpUtils = new HttpUtils();
		httpUtils.downloadFile(
				"https://cache-redirector.jetbrains.com/intellij-jbr/jbr_fd-21-osx-x64-b126.4.tar.gz", tarGzFile);
		var releaseInfo = ArchiveUtils.extractReleaseInfo(
				tarGzFile, tarGzFile.getFileName().toString());
		assertThat(releaseInfo).isNotNull();
	}
}
