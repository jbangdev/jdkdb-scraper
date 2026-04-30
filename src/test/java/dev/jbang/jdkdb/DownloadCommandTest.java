package dev.jbang.jdkdb;

import static org.assertj.core.api.Assertions.assertThat;

import dev.jbang.jdkdb.model.JdkMetadata;
import dev.jbang.jdkdb.scraper.DownloadResult;
import dev.jbang.jdkdb.util.MetadataUtils;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

class DownloadCommandTest {

	@Test
	void prioritizeMetadataKeepsMissingChecksumsFirstWithoutRandomization() {
		JdkMetadata missingReleaseInfo1 = metadataMissingReleaseInfo("release-info-only-1.tar.gz");
		JdkMetadata missingChecksum1 = metadataMissingChecksums("missing-checksum-1.tar.gz");
		JdkMetadata missingReleaseInfo2 = metadataMissingReleaseInfo("release-info-only-2.tar.gz");
		JdkMetadata missingChecksum2 = metadataMissingChecksums("missing-checksum-2.tar.gz");

		List<JdkMetadata> ordered = DownloadCommand.prioritizeMetadata(
				List.of(missingReleaseInfo1, missingChecksum1, missingReleaseInfo2, missingChecksum2), false);

		assertThat(ordered).hasSize(4);
		assertThat(ordered.subList(0, 2)).allMatch(MetadataUtils::hasMissingChecksums);
		assertThat(ordered.subList(2, 4)).noneMatch(MetadataUtils::hasMissingChecksums);
	}

	@Test
	void prioritizeMetadataRandomizesWithinBucketsButKeepsChecksumPriority() {
		List<JdkMetadata> input = List.of(
				metadataMissingReleaseInfo("release-info-only-1.tar.gz"),
				metadataMissingChecksums("missing-checksum-1.tar.gz"),
				metadataMissingReleaseInfo("release-info-only-2.tar.gz"),
				metadataMissingChecksums("missing-checksum-2.tar.gz"),
				metadataMissingChecksums("missing-checksum-3.tar.gz"));

		for (int i = 0; i < 25; i++) {
			List<JdkMetadata> ordered = DownloadCommand.prioritizeMetadata(input, true);
			int missingChecksumCount = (int)
					ordered.stream().filter(MetadataUtils::hasMissingChecksums).count();

			assertThat(ordered.subList(0, missingChecksumCount)).allMatch(MetadataUtils::hasMissingChecksums);
			assertThat(ordered.subList(missingChecksumCount, ordered.size()))
					.noneMatch(MetadataUtils::hasMissingChecksums);
		}
	}

	private JdkMetadata metadataMissingChecksums(String filename) {
		return JdkMetadata.create()
				.setDistro("test-distro")
				.setFilename(filename)
				.setReleaseType("ga")
				.setVersion("21.0.1")
				.setJavaVersion("21")
				.setJvmImpl("hotspot")
				.setOs("linux")
				.setArchitecture("x86_64")
				.setFileType("tar.gz")
				.setImageType("jdk")
				.setUrl("https://example.com/" + filename)
				.setReleaseInfo(Collections.emptyMap());
	}

	private JdkMetadata metadataMissingReleaseInfo(String filename) {
		return JdkMetadata.create()
				.setDistro("test-distro")
				.setFilename(filename)
				.setReleaseType("ga")
				.setVersion("21.0.1")
				.setJavaVersion("21")
				.setJvmImpl("hotspot")
				.setOs("linux")
				.setArchitecture("x86_64")
				.setFileType("tar.gz")
				.setImageType("jdk")
				.setUrl("https://example.com/" + filename)
				.download(new DownloadResult("md5", "sha1", "sha256", "sha512", 1L));
	}
}
