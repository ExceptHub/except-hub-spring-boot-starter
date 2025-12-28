package dev.excepthub.starter;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.ttddyy.dsproxy.listener.ChainListener;
import net.ttddyy.dsproxy.support.ProxyDataSourceBuilder;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.lang.Nullable;

import javax.sql.DataSource;

/**
 * Configures DataSource proxy to intercept all SQL queries.
 * This enables slow query detection and tracking.
 *
 * Only enabled when:
 * 1. datasource-proxy library is on classpath (@ConditionalOnClass)
 * 2. excepthub.slow-queries.enabled=true (default: true)
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
@ConditionalOnClass(name = "net.ttddyy.dsproxy.support.ProxyDataSourceBuilder")
@ConditionalOnProperty(prefix = "excepthub.slow-queries", name = "enabled", havingValue = "true", matchIfMissing = true)
public class ExceptHubDataSourceProxyConfig {

    private final ExceptHubClient exceptHubClient;
    private final ExceptHubProperties properties;

    /**
     * Wraps the default DataSource with a proxy that tracks query execution time.
     * BeanPostProcessor ensures this runs after DataSource bean is created.
     */
    @Bean
    public BeanPostProcessor exceptHubDataSourceProxyBeanPostProcessor() {
        log.info("🔧 Creating ExceptHubDataSourceProxyBeanPostProcessor...");
        return new BeanPostProcessor() {
            @Override
            @Nullable
            public Object postProcessAfterInitialization(Object bean, String beanName) {
                log.debug("🔍 Checking bean: {} (type: {})", beanName, bean.getClass().getName());

                // Wrap DataSource with proxy (but not if it's already a proxy)
                if (bean instanceof DataSource && !bean.getClass().getName().contains("Proxy")) {
                    log.info("✅ ExceptHub slow query detection enabled - wrapping DataSource bean: {}", beanName);
                    log.info("📊 Original DataSource class: {}", bean.getClass().getName());
                    DataSource proxied = createProxyDataSource((DataSource) bean);
                    log.info("📊 Proxied DataSource class: {}", proxied.getClass().getName());
                    return proxied;
                }
                return bean;
            }
        };
    }

    private DataSource createProxyDataSource(DataSource dataSource) {
        log.info("🔨 Building DataSource proxy...");
        long threshold = properties.getSlowQueries().getThresholdMs();
        log.info("⏰ Slow query threshold: {}ms", threshold);
        ExceptHubSlowQueryListener slowQueryListener = new ExceptHubSlowQueryListener(exceptHubClient, threshold);
        log.info("🎧 Created SlowQueryListener");

        ChainListener chainListener = new ChainListener();
        chainListener.addListener(slowQueryListener);
        log.info("⛓️ Added listener to chain");

        DataSource proxy = ProxyDataSourceBuilder
                .create(dataSource)
                .name("ExceptHubSlowQueryDataSourceProxy")
                .listener(chainListener)
                .proxyResultSet() // Also proxy ResultSet for detailed tracking
                .build();

        log.info("✅ DataSource proxy built successfully!");
        return proxy;
    }
}
