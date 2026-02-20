package dev.jbang.jdkdb.util;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import picocli.CommandLine.IVersionProvider;

/** Provides version information from build-time generated properties */
public class VersionProvider implements IVersionProvider {
	@Override
	public String[] getVersion() throws Exception {
		Properties props = new Properties();
		try (InputStream is = VersionProvider.class.getResourceAsStream("/version.properties")) {
			if (is != null) {
				props.load(is);
				String version = props.getProperty("version", "unknown");
				return new String[] {"jdkdb-scraper " + version};
			}
		} catch (IOException e) {
			// Fall back to unknown if we can't read the file
		}
		return new String[] {"jdkdb-scraper unknown"};
	}
}
