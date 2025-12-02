package dev.excepthub.starter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Configuration
@EnableConfigurationProperties(ExceptHubProperties.class)
@ConditionalOnProperty(prefix = "excepthub", name = "enabled", havingValue = "true", matchIfMissing = true)
public class ExceptHubAutoConfiguration {

    @Bean
    public ExceptHubClient exceptHubClient(ExceptHubProperties properties) {
        log.info("✅ ExceptHub starter enabled. Endpoint: {}", properties.getEndpoint());
        return new ExceptHubClient(properties);
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
}
