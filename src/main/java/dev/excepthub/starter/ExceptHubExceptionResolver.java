package dev.excepthub.starter;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.web.servlet.HandlerExceptionResolver;
import org.springframework.web.servlet.ModelAndView;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Catches ALL unhandled exceptions in Spring MVC controllers.
 * This is the safety net for exceptions that don't have @ExceptionHandler.
 *
 * These are the MOST CRITICAL errors - unhandled runtime exceptions, NPEs, etc.
 * If you have an @ExceptionHandler, you're prepared. If you don't - that's the real problem.
 */
@Slf4j
@RequiredArgsConstructor
public class ExceptHubExceptionResolver implements HandlerExceptionResolver, Ordered {

    private final ExceptHubClient exceptHubClient;

    @Override
    public int getOrder() {
        // Run BEFORE default exception resolvers (they have LOWEST_PRECEDENCE)
        // This way we catch exceptions before they're handled by Spring's default error page
        return Ordered.HIGHEST_PRECEDENCE;
    }

    @Override
    public ModelAndView resolveException(
            HttpServletRequest request,
            HttpServletResponse response,
            Object handler,
            Exception ex
    ) {
        // Send exception to ExceptHub asynchronously
        final Map<String, Object> httpContext = extractHttpContext(request);
        final String stackTrace = getStackTrace(ex);

        new Thread(() -> {
            try {
                log.debug("Sending unhandled exception to ExceptHub: {}", ex.getClass().getSimpleName());
                exceptHubClient.sendError(ex, stackTrace, httpContext);
            } catch (Exception e) {
                log.debug("Failed to send exception to ExceptHub", e);
            }
        }).start();

        // Return null to let other exception resolvers handle the response
        // (e.g., @ControllerAdvice, default error page)
        return null;
    }

    private Map<String, Object> extractHttpContext(HttpServletRequest request) {
        Map<String, Object> context = new HashMap<>();

        try {
            context.put("method", request.getMethod());

            // Build full URL with query string
            String url = request.getRequestURL().toString();
            String queryString = request.getQueryString();
            if (queryString != null && !queryString.isEmpty()) {
                url += "?" + queryString;
            }
            context.put("url", url);
            context.put("queryString", queryString);

            context.put("protocol", request.getProtocol());
            context.put("remoteAddr", request.getRemoteAddr());
            context.put("remoteHost", request.getRemoteHost());

            Map<String, String> headers = new HashMap<>();
            Collections.list(request.getHeaderNames())
                    .forEach(name -> headers.put(name, request.getHeader(name)));
            context.put("headers", headers);

            Map<String, String[]> params = request.getParameterMap();
            if (!params.isEmpty()) {
                context.put("parameters", params);
            }
        } catch (Exception e) {
            log.debug("Failed to extract HTTP context: {}", e.getMessage());
        }

        return context;
    }

    private String getStackTrace(Exception e) {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        e.printStackTrace(pw);
        return sw.toString();
    }
}
