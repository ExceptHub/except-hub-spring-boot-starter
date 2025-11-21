package ai.except.starter;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@RequiredArgsConstructor
public class ExceptAIClient {

    private final ExceptAIProperties properties;
    private final RestTemplate restTemplate = new RestTemplate();

    public void sendError(Exception exception, String stackTrace, Map<String, Object> httpContext) {
        if (!properties.isEnabled()) {
            return;
        }

        try {
            Map<String, Object> payload = buildPayload(exception, stackTrace, httpContext);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("X-API-Key", properties.getApiKey());

            log.info(headers.get("X-API-Key").toString());

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(payload, headers);

            restTemplate.postForEntity(
                    properties.getEndpoint(),
                    request,
                    String.class
            );

            log.debug("✅ Error sent to ExceptAI");

        } catch (Exception e) {
            log.error("❌ Failed to send error to ExceptAI: {}", e.getMessage());
        }
    }

    private Map<String, Object> buildPayload(Exception exception, String stackTrace, Map<String, Object> httpContext) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("timestamp", Instant.now().toString());
        payload.put("service", properties.getService());
        payload.put("environment", properties.getEnvironment());
        payload.put("gitSha", detectGitSha()); // ✅ Auto-detect

        Map<String, Object> exceptionData = new HashMap<>();
        exceptionData.put("type", exception.getClass().getSimpleName());
        exceptionData.put("message", exception.getMessage());
        exceptionData.put("stackTrace", stackTrace);

        payload.put("exception", exceptionData);
        payload.put("http", httpContext);

        return payload;
    }

    // ✅ Auto-detect Git SHA z CI/CD env vars
    private String detectGitSha() {
        // GitHub Actions
        String sha = System.getenv("GITHUB_SHA");
        if (sha != null && !sha.isEmpty()) {
            log.debug("Detected Git SHA from GITHUB_SHA: {}", sha);
            return sha;
        }

        // GitLab CI
        sha = System.getenv("CI_COMMIT_SHA");
        if (sha != null && !sha.isEmpty()) {
            log.debug("Detected Git SHA from CI_COMMIT_SHA: {}", sha);
            return sha;
        }

        // Jenkins
        sha = System.getenv("GIT_COMMIT");
        if (sha != null && !sha.isEmpty()) {
            log.debug("Detected Git SHA from GIT_COMMIT: {}", sha);
            return sha;
        }

        // CircleCI
        sha = System.getenv("CIRCLE_SHA1");
        if (sha != null && !sha.isEmpty()) {
            log.debug("Detected Git SHA from CIRCLE_SHA1: {}", sha);
            return sha;
        }

        // Fallback: master branch
        log.debug("No Git SHA detected, using fallback: master");
        return "master";
    }
}