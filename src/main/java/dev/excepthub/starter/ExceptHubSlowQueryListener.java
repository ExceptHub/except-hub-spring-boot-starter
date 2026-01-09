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

            // Only track slow queries
            if (elapsedTime < slowQueryThresholdMs) {
                return;
            }

            for (QueryInfo queryInfo : queryInfoList) {
                String query = queryInfo.getQuery();

                if (query == null || query.isEmpty()) {
                    continue;
                }

                // Skip internal framework queries (Hibernate metadata, etc.)
                if (isFrameworkQuery(query)) {
                    continue;
                }

                trackSlowQuery(query, elapsedTime);
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
     * We don't want to track these.
     */
    private boolean isFrameworkQuery(String query) {
        String upperQuery = query.trim().toUpperCase();

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
