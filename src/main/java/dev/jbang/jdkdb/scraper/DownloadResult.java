package dev.jbang.jdkdb.scraper;

/** Result of a file download */
public record DownloadResult(String md5, String sha1, String sha256, String sha512, long size) {}
