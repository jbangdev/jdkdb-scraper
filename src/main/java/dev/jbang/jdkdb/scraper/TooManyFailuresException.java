package dev.jbang.jdkdb.scraper;

public class TooManyFailuresException extends RuntimeException {
	public TooManyFailuresException(String message) {
		super(message);
	}
}
