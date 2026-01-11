package dev.excepthub.starter;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.ttddyy.dsproxy.ExecutionInfo;
import net.ttddyy.dsproxy.QueryInfo;
import net.ttddyy.dsproxy.listener.QueryExecutionListener;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;

/**
 * Listens to all SQL query executions via datasource-proxy.
 * Tracks slow queries (>500ms by default) and sends them to ExceptHub.
 */
@Slf4j
@RequiredArgsConstructor
public class ExceptHubSlowQueryListener implements QueryExecutionListener {

    private final ExceptHubClient exceptHubClient;
    private final long slowQueryThresholdMs;

    /**
     * Maximum query length to send (prevent sending huge queries)
     */
    private static final int MAX_QUERY_LENGTH = 10000;

    @Override
    public void afterQuery(ExecutionInfo execInfo, List<QueryInfo> queryInfoList) {
        try {
            long elapsedTime = execInfo.getElapsedTime();

            for (QueryInfo queryInfo : queryInfoList) {
                String query = queryInfo.getQuery();

                if (query == null || query.isEmpty()) {
                    continue;
                }

                // Skip internal framework queries (Hibernate metadata, etc.)
                if (isFrameworkQuery(query)) {
                    continue;
                }

                // ALWAYS record query in request context (for request-level aggregation)
                // This enables detection of slow requests caused by many fast queries (N+1)
                if (RequestQueryContext.isInitialized()) {
                    RequestQueryContext.recordQuery(query, elapsedTime);
                }

                // ALSO track individual slow queries (backward compatibility)
                // This catches single slow queries even outside HTTP request context
                if (elapsedTime >= slowQueryThresholdMs) {
                    trackSlowQuery(query, elapsedTime);
                }
            }
        } catch (Exception e) {
            // Don't let query tracking errors break the application
            log.debug("Error tracking slow query: {}", e.getMessage());
        }
    }

    @Override
    public void beforeQuery(ExecutionInfo execInfo, List<QueryInfo> queryInfoList) {
        // No action needed before query execution
    }

    private void trackSlowQuery(String originalQuery, long durationMs) {
        try {
            // Truncate query if too long
            if (originalQuery.length() > MAX_QUERY_LENGTH) {
                originalQuery = originalQuery.substring(0, MAX_QUERY_LENGTH) + "... [truncated]";
            }

            // Try to get HTTP context (endpoint + method) if available
            String endpoint = null;
            String httpMethod = null;

            try {
                ServletRequestAttributes attributes =
                        (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
                if (attributes != null) {
                    HttpServletRequest request = attributes.getRequest();
                    endpoint = request.getRequestURI();
                    httpMethod = request.getMethod();
                }
            } catch (Exception e) {
                // HTTP context not available (e.g., background task, scheduled job)
                log.debug("HTTP context not available for slow query tracking");
            }

            // Skip queries without HTTP context (e.g., DataInitializer, startup migrations)
            // These are one-time operations during app startup, not runtime performance issues
            if (endpoint == null) {
                log.debug("Skipping slow query tracking for non-HTTP context (startup/background): {}ms | {}",
                        durationMs,
                        originalQuery.length() > 100 ? originalQuery.substring(0, 100) + "..." : originalQuery);
                return;
            }

            log.warn("🐢 Slow query detected ({}ms): {} | Endpoint: {} {}",
                    durationMs,
                    originalQuery.length() > 100 ? originalQuery.substring(0, 100) + "..." : originalQuery,
                    httpMethod,
                    endpoint);

            // Send slow query to ExceptHub backend
            exceptHubClient.sendSlowQuery(originalQuery, durationMs, endpoint, httpMethod);

        } catch (Exception e) {
            log.debug("Error processing slow query: {}", e.getMessage());
        }
    }

    /**
     * Checks if query is an internal framework query (Hibernate metadata, etc.)
     * or DDL operation (schema changes during startup).
     * We don't want to track these.
     */
    private boolean isFrameworkQuery(String query) {
        String upperQuery = query.trim().toUpperCase();

        // Skip DDL operations (schema changes during startup)
        // These are normal during application initialization, not runtime slow queries
        if (upperQuery.startsWith("ALTER TABLE") ||
            upperQuery.startsWith("ALTER INDEX") ||
            upperQuery.startsWith("CREATE TABLE") ||
            upperQuery.startsWith("CREATE INDEX") ||
            upperQuery.startsWith("CREATE SEQUENCE") ||
            upperQuery.startsWith("CREATE UNIQUE INDEX") ||
            upperQuery.startsWith("DROP TABLE") ||
            upperQuery.startsWith("DROP INDEX") ||
            upperQuery.startsWith("DROP SEQUENCE") ||
            upperQuery.startsWith("TRUNCATE TABLE") ||
            upperQuery.startsWith("COMMENT ON")) {
            return true;
        }

        // Skip Hibernate sequence queries
        if (upperQuery.contains("NEXTVAL") || upperQuery.contains("SEQUENCE")) {
            return true;
        }

        // Skip schema metadata queries
        if (upperQuery.contains("INFORMATION_SCHEMA") ||
            upperQuery.contains("PG_CATALOG") ||
            upperQuery.contains("SHOW TABLES") ||
            upperQuery.contains("SHOW COLUMNS")) {
            return true;
        }

        // Skip Flyway migration tracking
        if (upperQuery.contains("FLYWAY_SCHEMA_HISTORY")) {
            return true;
        }

        return false;
    }
}
