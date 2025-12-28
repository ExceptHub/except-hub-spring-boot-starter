package dev.excepthub.starter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

@Slf4j
@Configuration
@EnableConfigurationProperties(ExceptHubProperties.class)
@ConditionalOnProperty(prefix = "excepthub", name = "enabled", havingValue = "true", matchIfMissing = true)
public class ExceptHubAutoConfiguration {

    @Bean
    public ExceptHubClient exceptHubClient(ExceptHubProperties properties, Environment springEnv) {
        // Auto-detect environment if not explicitly set
        if (properties.getEnvironment() == null || properties.getEnvironment().isEmpty()) {
            String detectedEnv = detectEnvironment(springEnv);
            properties.setEnvironment(detectedEnv);
            log.info("🔍 Auto-detected environment: {}", detectedEnv);
        }

        log.info("✅ ExceptHub starter enabled. Endpoint: {}, Environment: {}",
                properties.getEndpoint(), properties.getEnvironment());
        return new ExceptHubClient(properties);
    }

    /**
     * Auto-detect environment from:
     * 1. ENVIRONMENT env var (common in cloud providers)
     * 2. Spring active profile (first profile if multiple)
     * 3. Default to "unknown"
     */
    private String detectEnvironment(Environment springEnv) {
        // 1. Try ENVIRONMENT env var
        String envVar = System.getenv("ENVIRONMENT");
        if (envVar != null && !envVar.isEmpty()) {
            return normalizeEnvironment(envVar);
        }

        // 2. Try Spring active profiles
        String[] profiles = springEnv.getActiveProfiles();
        if (profiles.length > 0) {
            return normalizeEnvironment(profiles[0]);
        }

        // 3. Default
        return "unknown";
    }

    /**
     * Normalize environment names to standard values
     */
    private String normalizeEnvironment(String env) {
        if (env == null || env.isEmpty()) {
            return "unknown";
        }

        String normalized = env.toLowerCase().trim();

        // Production aliases
        if (normalized.matches("prod|production|prd|live")) {
            return "production";
        }

        // Development aliases
        if (normalized.matches("dev|development|develop")) {
            return "development";
        }

        // Staging aliases
        if (normalized.matches("stage|staging|stg|uat|preprod|pre-prod")) {
            return "staging";
        }

        // Local aliases
        if (normalized.matches("local|localhost|loc")) {
            return "local";
        }

        // Test aliases
        if (normalized.matches("test|testing|tst|qa")) {
            return "test";
        }

        // Return as-is if no match
        return normalized;
    }

    @Bean
    public ExceptHubExceptionHandler exceptHubExceptionHandler(ExceptHubClient client) {
        return new ExceptHubExceptionHandler(client);
    }

    @Bean
    public ExceptHubScheduledTaskAspect exceptHubScheduledTaskAspect(ExceptHubClient client) {
        log.info("✅ ExceptHub @Scheduled error monitoring enabled");
        return new ExceptHubScheduledTaskAspect(client);
    }

    @Bean
    public ExceptHubScheduledTaskRegistrar exceptHubScheduledTaskRegistrar(
            ExceptHubProperties properties,
            ApplicationContext applicationContext) {
        log.info("✅ ExceptHub @Scheduled task registry enabled");
        return new ExceptHubScheduledTaskRegistrar(properties, applicationContext);
    }
}
