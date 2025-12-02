package dev.excepthub.starter;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.*;

@Slf4j
@ControllerAdvice
@RequiredArgsConstructor
public class ExceptHubExceptionHandler {

    private final ExceptHubClient client;

    @ExceptionHandler(Exception.class)
    public void handleException(Exception exception, HttpServletRequest request) {
        log.error("❌ Exception caught: {}", exception.getMessage());

        String stackTrace = getStackTraceAsString(exception);
        Map<String, Object> httpContext = extractHttpContext(request);

        // Send to ExceptHub in background
        new Thread(() -> client.sendError(exception, stackTrace, httpContext)).start();

        // Re-throw
        if (exception instanceof RuntimeException) {
            throw (RuntimeException) exception;
        }
        throw new RuntimeException(exception);
    }

    private Map<String, Object> extractHttpContext(HttpServletRequest request) {
        Map<String, Object> context = new HashMap<>();

        try {
            // Basic request info
            context.put("method", request.getMethod());
            context.put("url", request.getRequestURL().toString());
            context.put("queryString", request.getQueryString());
            context.put("protocol", request.getProtocol());

            // Client info
            context.put("remoteAddr", request.getRemoteAddr());
            context.put("remoteHost", request.getRemoteHost());

            // Headers
            Map<String, String> headers = new HashMap<>();
            Collections.list(request.getHeaderNames())
                    .forEach(name -> headers.put(name, request.getHeader(name)));
            context.put("headers", headers);

            // Parameters
            Map<String, String[]> params = request.getParameterMap();
            if (!params.isEmpty()) {
                context.put("parameters", params);
            }

        } catch (Exception e) {
            log.warn("Failed to extract HTTP context: {}", e.getMessage());
        }

        return context;
    }

    private String getStackTraceAsString(Exception e) {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        e.printStackTrace(pw);
        return sw.toString();
    }
}
