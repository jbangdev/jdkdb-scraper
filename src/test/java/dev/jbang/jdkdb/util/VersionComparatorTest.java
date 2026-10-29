package dev.jbang.jdkdb.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class VersionComparatorTest {
	@ParameterizedTest
	@CsvSource({
		"21+35, 21.0.1+12",
		"21+35, 21.0.12.1+1",
		"17.0.8.1b1080.1, 17.0.14b1367.22",
		"17.0.9b1000.47, 17.0.10b829.26",
		"17.0.10b999.1, 17.0.10b1000.1",
		"17.0.10b1000.9, 17.0.10b1000.10",
		"21.0.1+9, 21.0.1+10",
		"8u322, 8u332"
	})
	void ordersReleaseBeforeBuild(String older, String newer) {
		assertThat(VersionComparator.INSTANCE.compare(older, newer)).isNegative();
		assertThat(VersionComparator.INSTANCE.compare(newer, older)).isPositive();
		assertThat(VersionComparator.INSTANCE.compare(older, older)).isZero();
	}
}
