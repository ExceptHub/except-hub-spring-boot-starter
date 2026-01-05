package dev.excepthub.starter;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

@Aspect
@Slf4j
@RequiredArgsConstructor
public class ExceptHubExceptionAspect {

    private final ExceptHubClient exceptHubClient;

    @Around("@annotation(org.springframework.web.bind.annotation.ExceptionHandler)")
    public Object aroundExceptionHandler(ProceedingJoinPoint joinPoint) throws Throwable {
        Object[] args = joinPoint.getArgs();
        Exception exception = null;

        for (Object arg : args) {
            if (arg instanceof Exception) {
                exception = (Exception) arg;
                break;
            }
        }

        if (exception != null) {
            final Exception capturedEx = exception;
            final Map<String, Object> httpContext = extractHttpContext();
            final String stackTrace = getStackTrace(capturedEx);

            new Thread(() -> {
                try {
                    log.debug("Sending exception to ExceptHub: {}", capturedEx.getClass().getSimpleName());
                    exceptHubClient.sendError(capturedEx, stackTrace, httpContext);
                } catch (Exception e) {
                    log.debug("Failed to send exception to ExceptHub", e);
                }
            }).start();
        }

        return joinPoint.proceed();
    }

    private Map<String, Object> extractHttpContext() {
        Map<String, Object> context = new HashMap<>();

        try {
            ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attributes != null) {
                HttpServletRequest request = attributes.getRequest();

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
