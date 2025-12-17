package dev.excepthub.starter;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "excepthub")
public class ExceptHubProperties {
    private boolean enabled = true;
    private String apiKey;
    private String endpoint = "https://exceptai.onrender.com/api/v1/errors";
    private String service = "unknown";
    private String environment = "production";
}
