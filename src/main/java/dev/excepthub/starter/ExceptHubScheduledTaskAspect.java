package dev.excepthub.starter;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.scheduling.annotation.Scheduled;

import java.io.PrintWriter;
import java.io.StringWriter;

/**
 * Aspect that automatically catches errors from @Scheduled methods
 * and sends them to ExceptHub without any user configuration
 */
@Aspect
@Slf4j
@RequiredArgsConstructor
public class ExceptHubScheduledTaskAspect {

    private final ExceptHubClient exceptHubClient;

    @Around("@annotation(scheduled)")
    public Object aroundScheduledTask(ProceedingJoinPoint joinPoint, Scheduled scheduled) throws Throwable {
        try {
            return joinPoint.proceed();
        } catch (Exception e) {
            // Log error locally
            log.error("❌ Error in scheduled task: {}.{}",
                joinPoint.getSignature().getDeclaringTypeName(),
                joinPoint.getSignature().getName(), e);

            // Send to ExceptHub
            try {
                MethodSignature signature = (MethodSignature) joinPoint.getSignature();

                String scheduledTaskClass = signature.getDeclaringTypeName();
                String scheduledTaskMethod = signature.getName();
                String cron = getCronExpression(scheduled);
                String stackTrace = getStackTrace(e);

                exceptHubClient.sendScheduledTaskError(
                    e,
                    stackTrace,
                    scheduledTaskClass,
                    scheduledTaskMethod,
                    cron
                );

            } catch (Exception sendError) {
                log.error("Failed to send scheduled task error to ExceptHub", sendError);
            }

            // DO NOT rethrow - allow scheduler to continue
            return null;
        }
    }

    private String getCronExpression(Scheduled scheduled) {
        if (!scheduled.cron().isEmpty()) {
            return scheduled.cron();
        }
        if (scheduled.fixedDelay() > 0) {
            return "fixedDelay=" + scheduled.fixedDelay() + "ms";
        }
        if (scheduled.fixedRate() > 0) {
            return "fixedRate=" + scheduled.fixedRate() + "ms";
        }
        if (!scheduled.fixedDelayString().isEmpty()) {
            return "fixedDelayString=" + scheduled.fixedDelayString();
        }
        if (!scheduled.fixedRateString().isEmpty()) {
            return "fixedRateString=" + scheduled.fixedRateString();
        }
        return "unknown";
    }

    private String getStackTrace(Exception e) {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        e.printStackTrace(pw);
        return sw.toString();
    }
}
