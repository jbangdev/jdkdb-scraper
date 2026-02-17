package dev.jbang.jdkdb.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import dev.jbang.jdkdb.scraper.DownloadResult;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
	"size",
	"release_info"
})
public class JdkMetadata {
	@JsonProperty("vendor")
	private String vendor;

	@JsonProperty("filename")
	private String filename;

	@JsonProperty("release_type")
	private String releaseType;

	@JsonProperty("version")
	private String version;

	@JsonProperty("java_version")
	private String javaVersion;

	@JsonProperty("jvm_impl")
	private String jvmImpl;

	@JsonProperty("os")
	private String os;

	@JsonProperty("architecture")
	private String architecture;

	@JsonProperty("file_type")
	private String fileType;

	@JsonProperty("image_type")
	private String imageType;

	@JsonProperty("features")
	private List<String> features;

	@JsonProperty("url")
	private String url;

	@JsonProperty("md5")
	private String md5;

	@JsonProperty("md5_file")
	private String md5File;

	@JsonProperty("sha1")
	private String sha1;

	@JsonProperty("sha1_file")
	private String sha1File;

	@JsonProperty("sha256")
	private String sha256;

	@JsonProperty("sha256_file")
	private String sha256File;

	@JsonProperty("sha512")
	private String sha512;

	@JsonProperty("sha512_file")
	private String sha512File;

	@JsonProperty("size")
	private long size;

	@JsonProperty("release_info")
	@JsonInclude(JsonInclude.Include.NON_NULL)
	private Map<String, String> releaseInfo;

	@JsonIgnore
	private transient Path metadataFile;

	// Constructors
	public JdkMetadata() {
		releaseType = "ga";
		jvmImpl = "hotspot";
		imageType = "jdk";
		features = new ArrayList<>();
	}

	// Getters and Setters
	public String vendor() {
		return vendor;
	}

	public JdkMetadata vendor(String vendor) {
		this.vendor = vendor;
		return this;
	}

	public String filename() {
		return filename;
	}

	public JdkMetadata filename(String filename) {
		this.filename = filename;
		return this;
	}

	public String releaseType() {
		return releaseType;
	}

	public JdkMetadata releaseType(String releaseType) {
		this.releaseType = releaseType;
		return this;
	}

	public String version() {
		return version;
	}

	public JdkMetadata version(String version) {
		this.version = version;
		return this;
	}

	public String javaVersion() {
		return javaVersion;
	}

	public JdkMetadata javaVersion(String javaVersion) {
		this.javaVersion = javaVersion;
		return this;
	}

	public String jvmImpl() {
		return jvmImpl;
	}

	public JdkMetadata jvmImpl(String jvmImpl) {
		this.jvmImpl = jvmImpl;
		return this;
	}

	public String os() {
		return os;
	}

	public JdkMetadata os(String os) {
		this.os = os;
		return this;
	}

	public String arch() {
		return architecture;
	}

	public JdkMetadata arch(String architecture) {
		this.architecture = architecture;
		return this;
	}

	public String fileType() {
		return fileType;
	}

	public JdkMetadata fileType(String fileType) {
		this.fileType = fileType;
		return this;
	}

	public String imageType() {
		return imageType;
	}

	public JdkMetadata imageType(String imageType) {
		this.imageType = imageType;
		return this;
	}

	public List<String> features() {
		return features;
	}

	public JdkMetadata features(List<String> features) {
		this.features = features;
		return this;
	}

	public String url() {
		return url;
	}

	public JdkMetadata url(String url) {
		this.url = url;
		return this;
	}

	public String md5() {
		return md5;
	}

	public String md5File() {
		return md5File;
	}

	public String sha1() {
		return sha1;
	}

	public String sha1File() {
		return sha1File;
	}

	public String sha256() {
		return sha256;
	}

	public String sha256File() {
		return sha256File;
	}

	public String sha512() {
		return sha512;
	}

	public String sha512File() {
		return sha512File;
	}

	public long size() {
		return size;
	}

	public Map<String, String> releaseInfo() {
		return releaseInfo;
	}

	public JdkMetadata releaseInfo(Map<String, String> releaseInfo) {
		this.releaseInfo = releaseInfo;
		return this;
	}

	public Path metadataFile() {
		if (metadataFile == null && filename != null) {
			return Path.of(filename + ".json");
		}
		return metadataFile;
	}

	public JdkMetadata metadataFile(Path metadataFile) {
		this.metadataFile = metadataFile;
		return this;
	}

	public JdkMetadata download(DownloadResult download) {
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

	public static JdkMetadata create() {
		return new JdkMetadata();
	}
}
