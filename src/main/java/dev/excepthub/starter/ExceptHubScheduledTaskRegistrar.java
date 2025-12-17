package dev.excepthub.starter;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationContext;
import org.springframework.context.event.EventListener;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.client.RestTemplate;

import java.lang.reflect.Method;
import java.util.*;

/**
 * Registers all @Scheduled tasks with ExceptHub on application startup
 */
@Slf4j
@RequiredArgsConstructor
public class ExceptHubScheduledTaskRegistrar {

    private final ExceptHubProperties properties;
    private final ApplicationContext applicationContext;
    private final RestTemplate restTemplate = new RestTemplate();

    @EventListener(ApplicationReadyEvent.class)
    public void registerScheduledTasks() {
        if (!properties.isEnabled()) {
            return;
        }

        if (properties.getApiKey() == null || properties.getApiKey().trim().isEmpty()) {
            return;
        }

        try {
            List<Map<String, String>> tasks = findAllScheduledTasks();

            if (tasks.isEmpty()) {
                log.info("No @Scheduled tasks found to register");
                return;
            }

            sendTaskRegistration(tasks);
            log.info("✅ Registered {} @Scheduled tasks with ExceptHub", tasks.size());

        } catch (Exception e) {
            log.error("❌ Failed to register scheduled tasks: {}", e.getMessage());
        }
    }

    private List<Map<String, String>> findAllScheduledTasks() {
        List<Map<String, String>> tasks = new ArrayList<>();

        String[] beanNames = applicationContext.getBeanDefinitionNames();

        for (String beanName : beanNames) {
            try {
                Object bean = applicationContext.getBean(beanName);
                Class<?> beanClass = bean.getClass();

                // Skip proxy classes
                if (beanClass.getName().contains("$$")) {
                    beanClass = beanClass.getSuperclass();
                }

                for (Method method : beanClass.getDeclaredMethods()) {
                    Scheduled scheduled = method.getAnnotation(Scheduled.class);
                    if (scheduled != null) {
                        Map<String, String> task = new HashMap<>();
                        task.put("taskClass", beanClass.getName());
                        task.put("taskMethod", method.getName());
                        task.put("cronExpression", getCronExpression(scheduled));
                        tasks.add(task);
                    }
                }
            } catch (Exception e) {
                // Skip beans that cannot be introspected
            }
        }

        return tasks;
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

    private void sendTaskRegistration(List<Map<String, String>> tasks) {
        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("tasks", tasks);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("X-API-Key", properties.getApiKey());

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(payload, headers);

            String endpoint = properties.getEndpoint().replace("/errors", "/cron/register");

            restTemplate.postForEntity(endpoint, request, String.class);

        } catch (Exception e) {
            throw new RuntimeException("Failed to send task registration", e);
        }
    }
}
