package dev.excepthub.starter;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import net.ttddyy.dsproxy.listener.ChainListener;
import net.ttddyy.dsproxy.support.ProxyDataSourceBuilder;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Role;
import org.springframework.lang.Nullable;

import javax.sql.DataSource;

/**
 * Configures DataSource proxy to intercept all SQL queries.
 * This enables slow query detection and tracking.
 *
 * Only enabled when:
 * 1. ExceptHubClient bean is available (@ConditionalOnBean)
 * 2. datasource-proxy library is on classpath (@ConditionalOnClass)
 * 3. excepthub.slow-queries.enabled=true (default: true)
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
@ConditionalOnBean(ExceptHubClient.class)
@ConditionalOnClass(name = "net.ttddyy.dsproxy.support.ProxyDataSourceBuilder")
@ConditionalOnProperty(prefix = "excepthub.slow-queries", name = "enabled", havingValue = "true", matchIfMissing = true)
public class ExceptHubDataSourceProxyConfig {

    private final ExceptHubClient exceptHubClient;
    private final ExceptHubProperties properties;

    /**
     * Wraps the default DataSource with a proxy that tracks query execution time.
     * BeanPostProcessor ensures this runs after DataSource bean is created.
     *
     * Marked as ROLE_INFRASTRUCTURE to avoid BeanPostProcessor warnings during startup.
     * Uses static method to ensure early initialization without circular dependencies.
     */
    @Bean
    @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
    public static BeanPostProcessor exceptHubDataSourceProxyBeanPostProcessor(
            ExceptHubClient exceptHubClient,
            ExceptHubProperties properties) {
        Logger logger = LoggerFactory.getLogger(ExceptHubDataSourceProxyConfig.class);
        return new BeanPostProcessor() {
            @Override
            @Nullable
            public Object postProcessAfterInitialization(Object bean, String beanName) {
                // Wrap DataSource with proxy (but not if it's already a proxy)
                if (bean instanceof DataSource && !bean.getClass().getName().contains("Proxy")) {
                    logger.info("✅ ExceptHub slow query detection enabled (threshold: {}ms)", properties.getSlowQueries().getThresholdMs());
                    DataSource proxied = createProxyDataSource((DataSource) bean, exceptHubClient, properties);
                    return proxied;
                }
                return bean;
            }
        };
    }

    private static DataSource createProxyDataSource(
            DataSource dataSource,
            ExceptHubClient exceptHubClient,
            ExceptHubProperties properties) {
        long threshold = properties.getSlowQueries().getThresholdMs();
        ExceptHubSlowQueryListener slowQueryListener = new ExceptHubSlowQueryListener(exceptHubClient, threshold);

        ChainListener chainListener = new ChainListener();
        chainListener.addListener(slowQueryListener);

        return ProxyDataSourceBuilder
                .create(dataSource)
                .name("ExceptHubSlowQueryDataSourceProxy")
                .listener(chainListener)
                .proxyResultSet() // Also proxy ResultSet for detailed tracking
                .build();
    }
}
