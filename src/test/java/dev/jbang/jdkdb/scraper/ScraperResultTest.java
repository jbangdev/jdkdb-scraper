package dev.jbang.jdkdb.scraper;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.Test;

class ScraperResultTest {

	@Test
	void testSuccessResult() {
		// When
		ScraperResult result = ScraperResult.success(10, 2);

		// Then
		assertThat(result).isNotNull();
		assertThat(result.success()).isTrue();
		assertThat(result.itemsProcessed()).isEqualTo(10);
		assertThat(result.itemsSkipped()).isEqualTo(2);
		assertThat(result.error()).isNull();
	}

	@Test
	void testSuccessResultWithZeroItems() {
		// When
		ScraperResult result = ScraperResult.success(0, 0);

		// Then
		assertThat(result).isNotNull();
		assertThat(result.success()).isTrue();
		assertThat(result.itemsProcessed()).isEqualTo(0);
		assertThat(result.itemsSkipped()).isEqualTo(0);
		assertThat(result.error()).isNull();
	}

	@Test
	void testFailureResult() {
		// Given
		Exception exception = new RuntimeException("Test error");

		// When
		ScraperResult result = ScraperResult.failure(exception);

		// Then
		assertThat(result).isNotNull();
		assertThat(result.success()).isFalse();
		assertThat(result.itemsProcessed()).isEqualTo(0);
		assertThat(result.error()).isEqualTo(exception);
		assertThat(result.error().getMessage()).isEqualTo("Test error");
	}

	@Test
	void testFailureResultWithNullException() {
		// When
		ScraperResult result = ScraperResult.failure(null);

		// Then
		assertThat(result).isNotNull();
		assertThat(result.success()).isFalse();
		assertThat(result.itemsProcessed()).isEqualTo(0);
		assertThat(result.error()).isNull();
	}

	@Test
	void testSuccessResultToString() {
		// Given
		ScraperResult result = ScraperResult.success(5, 2);

		// When
		String str = result.toString();

		// Then
		assertThat(str).isEqualTo("SUCCESS (5 items processed, 2 items skipped)");
	}

	@Test
	void testFailureResultToString() {
		// Given
		Exception exception = new RuntimeException("Something went wrong");
		ScraperResult result = ScraperResult.failure(exception);

		// When
		String str = result.toString();

		// Then
		assertThat(str).isEqualTo("FAILED - Something went wrong");
	}

	@Test
	void testFailureResultToStringWithNullException() {
		// Given
		ScraperResult result = ScraperResult.failure(null);

		// When
		String str = result.toString();

		// Then
		assertThat(str).isEqualTo("FAILED - Unknown error");
	}

	@Test
	void testResultEquality() {
		// Given
		ScraperResult result1 = ScraperResult.success(10, 2);
		ScraperResult result2 = ScraperResult.success(10, 2);
		ScraperResult result3 = ScraperResult.success(5, 1);

		// Then
		assertThat(result1).isEqualTo(result2);
		assertThat(result1).isNotEqualTo(result3);
	}

	@Test
	void testFailureResultEquality() {
		// Given
		Exception exception1 = new RuntimeException("error");
		Exception exception2 = new RuntimeException("error");
		ScraperResult result1 = ScraperResult.failure(exception1);
		ScraperResult result2 = ScraperResult.failure(exception1);
		ScraperResult result3 = ScraperResult.failure(exception2);

		// Then
		assertThat(result1).isEqualTo(result2);
		// Different exception instances are not equal
		assertThat(result1).isNotEqualTo(result3);
	}
}
