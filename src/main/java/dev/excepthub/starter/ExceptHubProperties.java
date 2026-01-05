package dev.excepthub.starter;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "excepthub")
public class ExceptHubProperties {
    private boolean enabled = true;
    private String apiKey;
    private String endpoint = "https://exceptai.onrender.com/api/v1/errors";
    private String service = "unknown";

    /**
     * Environment name (e.g., "production", "development", "staging", "local").
     * If not set, will be auto-detected from:
     * 1. ENVIRONMENT env var
     * 2. Spring active profile
     * 3. Defaults to "unknown"
     */
    private String environment;

    /**
     * Release version (e.g., "1.2.3", "v2.0.0").
     * If not set, will be auto-detected from:
     * 1. EXCEPTHUB_RELEASE env var
     * 2. RELEASE_VERSION env var
     * 3. Git tag (if available)
     * 4. Defaults to git commit SHA
     */
    private String release;

    /**
     * Slow query detection settings
     */
    private SlowQueries slowQueries = new SlowQueries();

    @Data
    public static class SlowQueries {
        /**
         * Threshold in milliseconds for detecting slow queries.
         * Default: 500ms
         */
        private long thresholdMs = 500;

        /**
         * Whether slow query detection is enabled.
         * Default: true (enabled if datasource-proxy is on classpath)
         */
        private boolean enabled = true;
    }
}
