package dev.excepthub.starter;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;

/**
 * Thread-local context for tracking all SQL queries within a single HTTP request.
 * This enables detection of slow queries at the REQUEST level, not just individual query level.
 *
 * Example: N+1 problem - 100 fast queries (5ms each = 500ms total) should be detected
 * as a slow request even though each individual query is fast.
 */
@Slf4j
public class RequestQueryContext {

    private static final ThreadLocal<RequestContext> CONTEXT = new ThreadLocal<>();

    /**
     * Initialize context at the start of a request
     */
    public static void initialize(String endpoint, String httpMethod) {
        RequestContext context = new RequestContext();
        context.endpoint = endpoint;
        context.httpMethod = httpMethod;
        context.queries = new ArrayList<>();
        context.totalDurationMs = 0;
        context.startTimeMs = System.currentTimeMillis();
        CONTEXT.set(context);
    }

    /**
     * Record a query execution
     */
    public static void recordQuery(String query, long durationMs) {
        RequestContext context = CONTEXT.get();
        if (context != null) {
            QueryExecution queryExec = new QueryExecution();
            queryExec.query = query;
            queryExec.durationMs = durationMs;
            context.queries.add(queryExec);
            context.totalDurationMs += durationMs;
        }
    }

    /**
     * Get current context (returns null if no request context)
     */
    public static RequestContext get() {
        return CONTEXT.get();
    }

    /**
     * Clear context at the end of request (important to avoid memory leaks!)
     */
    public static void clear() {
        CONTEXT.remove();
    }

    /**
     * Check if context is initialized
     */
    public static boolean isInitialized() {
        return CONTEXT.get() != null;
    }

    @Data
    public static class RequestContext {
        private String endpoint;
        private String httpMethod;
        private List<QueryExecution> queries;
        private long totalDurationMs;
        private long startTimeMs;

        public int getQueryCount() {
            return queries != null ? queries.size() : 0;
        }
    }

    @Data
    public static class QueryExecution {
        private String query;
        private long durationMs;
    }
}
