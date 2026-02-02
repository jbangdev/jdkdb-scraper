package dev.jbang.jdkdb.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Objects;

/** Represents JDK metadata for a specific release */
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

	// Constructors
	public JdkMetadata() {}

	// Getters and Setters
	public String getVendor() {
		return vendor;
	}

	public void setVendor(String vendor) {
		this.vendor = vendor;
	}

	public String getFilename() {
		return filename;
	}

	public void setFilename(String filename) {
		this.filename = filename;
	}

	public String getReleaseType() {
		return releaseType;
	}

	public void setReleaseType(String releaseType) {
		this.releaseType = releaseType;
	}

	public String getVersion() {
		return version;
	}

	public void setVersion(String version) {
		this.version = version;
	}

	public String getJavaVersion() {
		return javaVersion;
	}

	public void setJavaVersion(String javaVersion) {
		this.javaVersion = javaVersion;
	}

	public String getJvmImpl() {
		return jvmImpl;
	}

	public void setJvmImpl(String jvmImpl) {
		this.jvmImpl = jvmImpl;
	}

	public String getOs() {
		return os;
	}

	public void setOs(String os) {
		this.os = os;
	}

	public String getArchitecture() {
		return architecture;
	}

	public void setArchitecture(String architecture) {
		this.architecture = architecture;
	}

	public String getFileType() {
		return fileType;
	}

	public void setFileType(String fileType) {
		this.fileType = fileType;
	}

	public String getImageType() {
		return imageType;
	}

	public void setImageType(String imageType) {
		this.imageType = imageType;
	}

	public List<String> getFeatures() {
		return features;
	}

	public void setFeatures(List<String> features) {
		this.features = features;
	}

	public String getUrl() {
		return url;
	}

	public void setUrl(String url) {
		this.url = url;
	}

	public String getMd5() {
		return md5;
	}

	public void setMd5(String md5) {
		this.md5 = md5;
	}

	public String getMd5File() {
		return md5File;
	}

	public void setMd5File(String md5File) {
		this.md5File = md5File;
	}

	public String getSha1() {
		return sha1;
	}

	public void setSha1(String sha1) {
		this.sha1 = sha1;
	}

	public String getSha1File() {
		return sha1File;
	}

	public void setSha1File(String sha1File) {
		this.sha1File = sha1File;
	}

	public String getSha256() {
		return sha256;
	}

	public void setSha256(String sha256) {
		this.sha256 = sha256;
	}

	public String getSha256File() {
		return sha256File;
	}

	public void setSha256File(String sha256File) {
		this.sha256File = sha256File;
	}

	public String getSha512() {
		return sha512;
	}

	public void setSha512(String sha512) {
		this.sha512 = sha512;
	}

	public String getSha512File() {
		return sha512File;
	}

	public void setSha512File(String sha512File) {
		this.sha512File = sha512File;
	}

	public long getSize() {
		return size;
	}

	public void setSize(long size) {
		this.size = size;
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
}
