package dev.jbang.jdkdb.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import dev.jbang.jdkdb.scraper.DownloadResult;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Represents JDK metadata for a specific release */
@JsonPropertyOrder({
	"vendor",
	"filename",
	"release_type",
	"version",
	"java_version",
	"jvm_impl",
	"os",
	"architecture",
	"file_type",
	"image_type",
	"features",
	"url",
	"md5",
	"md5_file",
	"sha1",
	"sha1_file",
	"sha256",
	"sha256_file",
	"sha512",
	"sha512_file",
	"size"
})
public class JdkMetadata {
	private String vendor;
	private String filename;

	@JsonProperty("release_type")
	private String releaseType;

	private String version;

	@JsonProperty("java_version")
	private String javaVersion;

	@JsonProperty("jvm_impl")
	private String jvmImpl;

	private String os;
	private String architecture;

	@JsonProperty("file_type")
	private String fileType;

	@JsonProperty("image_type")
	private String imageType;

	private List<String> features;
	private String url;

	private String md5;

	@JsonProperty("md5_file")
	private String md5File;

	private String sha1;

	@JsonProperty("sha1_file")
	private String sha1File;

	private String sha256;

	@JsonProperty("sha256_file")
	private String sha256File;

	private String sha512;

	@JsonProperty("sha512_file")
	private String sha512File;

	private long size;

	@JsonIgnore
	private transient String metadataFilename;

	// Constructors
	public JdkMetadata() {}

	// Getters and Setters
	public String getVendor() {
		return vendor;
	}

	public String getFilename() {
		return filename;
	}

	public String getReleaseType() {
		return releaseType;
	}

	public String getVersion() {
		return version;
	}

	public String getJavaVersion() {
		return javaVersion;
	}

	public String getJvmImpl() {
		return jvmImpl;
	}

	public String getOs() {
		return os;
	}

	public String getArchitecture() {
		return architecture;
	}

	public String getFileType() {
		return fileType;
	}

	public String getImageType() {
		return imageType;
	}

	public List<String> getFeatures() {
		return features;
	}

	public String getUrl() {
		return url;
	}

	public String getMd5() {
		return md5;
	}

	public String getMd5File() {
		return md5File;
	}

	public String getSha1() {
		return sha1;
	}

	public String getSha1File() {
		return sha1File;
	}

	public String getSha256() {
		return sha256;
	}

	public String getSha256File() {
		return sha256File;
	}

	public String getSha512() {
		return sha512;
	}

	public String getSha512File() {
		return sha512File;
	}

	public long getSize() {
		return size;
	}

	public String getMetadataFilename() {
		if (metadataFilename == null) {
			throw new IllegalStateException(
					"Trying to access ignored value 'metadataFilename' with data read from file");
		}
		return metadataFilename;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;
		JdkMetadata that = (JdkMetadata) o;
		return Objects.equals(vendor, that.vendor)
				&& Objects.equals(filename, that.filename)
				&& Objects.equals(version, that.version);
	}

	@Override
	public int hashCode() {
		return Objects.hash(vendor, filename, version);
	}

	public static Builder builder() {
		return new Builder();
	}

	/**
	 * Builder class for JdkMetadata to reduce parameter count and improve readability
	 */
	public static class Builder {
		public String vendor;
		public String filename;
		public String releaseType;
		public String version;
		public String javaVersion;
		public String jvmImpl;
		public String os;
		public String arch;
		public String fileType;
		public String imageType;
		public List<String> features;
		public String url;
		public String md5;
		public String md5File;
		public String sha1;
		public String sha1File;
		public String sha256;
		public String sha256File;
		public String sha512;
		public String sha512File;
		public long size;
		public String metadataFilename;

		private Builder() {}

		public Builder vendor(String vendor) {
			this.vendor = vendor;
			return this;
		}

		public Builder filename(String filename) {
			this.filename = filename;
			return this;
		}

		public Builder releaseType(String releaseType) {
			this.releaseType = releaseType;
			return this;
		}

		public Builder version(String version) {
			this.version = version;
			return this;
		}

		public Builder javaVersion(String javaVersion) {
			this.javaVersion = javaVersion;
			return this;
		}

		public Builder jvmImpl(String jvmImpl) {
			this.jvmImpl = jvmImpl;
			return this;
		}

		public Builder os(String os) {
			this.os = os;
			return this;
		}

		public Builder arch(String arch) {
			this.arch = arch;
			return this;
		}

		public Builder fileType(String fileType) {
			this.fileType = fileType;
			return this;
		}

		public Builder imageType(String imageType) {
			this.imageType = imageType;
			return this;
		}

		public Builder features(List<String> features) {
			this.features = features;
			return this;
		}

		public Builder url(String url) {
			this.url = url;
			return this;
		}

		public Builder md5(String md5) {
			this.md5 = md5;
			return this;
		}

		public Builder md5File(String md5File) {
			this.md5File = md5File;
			return this;
		}

		public Builder sha1(String sha1) {
			this.sha1 = sha1;
			return this;
		}

		public Builder sha1File(String sha1File) {
			this.sha1File = sha1File;
			return this;
		}

		public Builder sha256(String sha256) {
			this.sha256 = sha256;
			return this;
		}

		public Builder sha256File(String sha256File) {
			this.sha256File = sha256File;
			return this;
		}

		public Builder sha512(String sha512) {
			this.sha512 = sha512;
			return this;
		}

		public Builder sha512File(String sha512File) {
			this.sha512File = sha512File;
			return this;
		}

		public Builder size(long size) {
			this.size = size;
			return this;
		}

		public Builder metadataFilename(String metadataFilename) {
			this.metadataFilename = metadataFilename;
			return this;
		}

		public Builder download(String filename, DownloadResult download) {
			this.filename = filename;
			this.md5 = download.md5();
			this.md5File = filename + ".md5";
			this.sha1 = download.sha1();
			this.sha1File = filename + ".sha1";
			this.sha256 = download.sha256();
			this.sha256File = filename + ".sha256";
			this.sha512 = download.sha512();
			this.sha512File = filename + ".sha512";
			this.size = download.size();
			return this;
		}

		public JdkMetadata build() {
			JdkMetadata metadata = new JdkMetadata();
			metadata.vendor = vendor;
			metadata.filename = filename;
			metadata.releaseType = releaseType != null ? releaseType : "ga";
			metadata.version = version;
			metadata.javaVersion = javaVersion;
			metadata.jvmImpl = jvmImpl != null ? jvmImpl : "hotspot";
			metadata.os = os;
			metadata.architecture = arch;
			metadata.fileType = fileType;
			metadata.imageType = imageType != null ? imageType : "jdk";
			metadata.features = features != null ? features : new ArrayList<>();
			metadata.url = url;
			metadata.md5 = md5;
			metadata.md5File = md5File;
			metadata.sha1 = sha1;
			metadata.sha1File = sha1File;
			metadata.sha256 = sha256;
			metadata.sha256File = sha256File;
			metadata.sha512 = sha512;
			metadata.sha512File = sha512File;
			metadata.size = size;
			metadata.metadataFilename = metadataFilename != null ? metadataFilename : filename + ".json";
			return metadata;
		}
	}

	public void setMetadataFilename(String metadataFilename) {
		this.metadataFilename = metadataFilename;
	}
}
