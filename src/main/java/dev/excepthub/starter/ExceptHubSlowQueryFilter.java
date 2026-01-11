package dev.excepthub.starter;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;

/**
 * Servlet Filter that tracks all SQL queries within a single HTTP request.
 * This enables detection of slow requests caused by multiple queries (N+1 problem).
 *
 * Flow:
 * 1. Initialize RequestQueryContext at request start
 * 2. ExceptHubSlowQueryListener records each query in the context
 * 3. At request end, check if total query time exceeds threshold
 * 4. If slow, send aggregated information to ExceptHub
 * 5. Clear context to avoid memory leaks
 */
@Slf4j
@RequiredArgsConstructor
public class ExceptHubSlowQueryFilter implements Filter {

    private final ExceptHubClient exceptHubClient;
    private final long slowQueryThresholdMs;

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        if (!(request instanceof HttpServletRequest)) {
            chain.doFilter(request, response);
            return;
        }

        HttpServletRequest httpRequest = (HttpServletRequest) request;
        String endpoint = httpRequest.getRequestURI();
        String httpMethod = httpRequest.getMethod();

        // Initialize request context
        RequestQueryContext.initialize(endpoint, httpMethod);

        try {
            // Process request (queries will be recorded in context)
            chain.doFilter(request, response);

        } finally {
            // Check if request was slow due to accumulated query time
            checkAndReportSlowRequest();

            // CRITICAL: Clear ThreadLocal to avoid memory leaks
            RequestQueryContext.clear();
        }
    }

    /**
     * Check if the total query time for this request exceeds the threshold.
     * If yes, report it as a slow query to ExceptHub.
     */
    private void checkAndReportSlowRequest() {
        try {
            RequestQueryContext.RequestContext context = RequestQueryContext.get();

            if (context == null) {
                return;
            }

            long totalQueryTime = context.getTotalDurationMs();
            int queryCount = context.getQueryCount();

            // Only report if total time exceeds threshold
            if (totalQueryTime < slowQueryThresholdMs) {
                return;
            }

            // Calculate request duration
            long requestDurationMs = System.currentTimeMillis() - context.getStartTimeMs();

            log.warn("🐢 Slow request detected: {} {} | Total queries: {} | Total query time: {}ms | Request duration: {}ms",
                    context.getHttpMethod(),
                    context.getEndpoint(),
                    queryCount,
                    totalQueryTime,
                    requestDurationMs);

            // Build aggregated query summary
            String aggregatedQuery = buildAggregatedQuerySummary(context);

            // Send to ExceptHub with aggregated information
            exceptHubClient.sendSlowQuery(
                    aggregatedQuery,
                    totalQueryTime,
                    context.getEndpoint(),
                    context.getHttpMethod()
            );

        } catch (Exception e) {
            log.debug("Error checking slow request: {}", e.getMessage());
        }
    }

    /**
     * Build a summary of all queries executed in this request.
     * For N+1 problems, shows all queries and highlights duplicates.
     */
    private String buildAggregatedQuerySummary(RequestQueryContext.RequestContext context) {
        StringBuilder summary = new StringBuilder();
        summary.append(String.format("[REQUEST AGGREGATED SLOW QUERY] Total %d queries, %dms total\n\n",
                context.getQueryCount(),
                context.getTotalDurationMs()));

        // List all queries with their durations
        int index = 1;
        for (RequestQueryContext.QueryExecution query : context.getQueries()) {
            summary.append(String.format("Query #%d (%dms):\n%s\n\n",
                    index++,
                    query.getDurationMs(),
                    truncateQuery(query.getQuery(), 500)));
        }

        return summary.toString();
    }

    /**
     * Truncate query if too long
     */
    private String truncateQuery(String query, int maxLength) {
        if (query == null) {
            return "";
        }
        if (query.length() <= maxLength) {
            return query;
        }
        return query.substring(0, maxLength) + "... [truncated]";
    }
}
