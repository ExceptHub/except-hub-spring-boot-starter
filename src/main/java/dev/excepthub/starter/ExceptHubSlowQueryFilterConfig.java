package dev.excepthub.starter;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configures the servlet filter for request-level slow query aggregation.
 * This enables detection of slow requests caused by multiple queries (N+1 problem).
 *
 * Only enabled when:
 * 1. This is a web application (@ConditionalOnWebApplication)
 * 2. ExceptHubClient bean is available (@ConditionalOnBean)
 * 3. excepthub.slow-queries.enabled=true (default: true)
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnBean(ExceptHubClient.class)
@ConditionalOnProperty(prefix = "excepthub.slow-queries", name = "enabled", havingValue = "true", matchIfMissing = true)
public class ExceptHubSlowQueryFilterConfig {

    private final ExceptHubClient exceptHubClient;
    private final ExceptHubProperties properties;

    /**
     * Registers the slow query filter with high priority to track all HTTP requests.
     * The filter will aggregate query times for each request and report slow requests.
     */
    @Bean
    public FilterRegistrationBean<ExceptHubSlowQueryFilter> exceptHubSlowQueryFilter() {
        FilterRegistrationBean<ExceptHubSlowQueryFilter> registrationBean = new FilterRegistrationBean<>();

        long threshold = properties.getSlowQueries().getThresholdMs();
        ExceptHubSlowQueryFilter filter = new ExceptHubSlowQueryFilter(exceptHubClient, threshold);

        registrationBean.setFilter(filter);
        registrationBean.addUrlPatterns("/*");
        registrationBean.setOrder(1); // High priority - run early in filter chain

//        log.info("✅ ExceptHub request-level slow query aggregation enabled (threshold: {}ms)", threshold);

        return registrationBean;
    }
}
