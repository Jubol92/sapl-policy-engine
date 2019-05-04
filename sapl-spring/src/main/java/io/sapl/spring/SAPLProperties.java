package io.sapl.spring;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import lombok.Data;

@Data
@Validated
@ConfigurationProperties(prefix = "io.sapl")
public class SAPLProperties {

	public enum PDPType {
		RESOURCES, FILESYSTEM, REMOTE
	}

	public enum PRPIndexType {
		SIMPLE, FAST
	}

	private PDPType type = PDPType.RESOURCES;

	private PRPIndexType index = PRPIndexType.SIMPLE;

	private Resources resources = new Resources();

	private Filesystem filesystem = new Filesystem();

	private Remote remote = new Remote();

	private boolean policyEnforcementFilter;

	@Data
	public static class Filesystem {
		private String policiesPath = "~/policies";
	}

	@Data
	public static class Resources {
		private String policiesPath = "/policies";
	}

	@Data
	public static class Remote {
		private static final int DEFAULT_REMOTE_PORT = 8443;
		private boolean active;
		private String host = "localhost";
		private int port = DEFAULT_REMOTE_PORT;
		private String key;
		private String secret;
	}

}