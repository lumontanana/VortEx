package com.vortex.vortexweb.notifications;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "vortex.notifications.email")
public record EmailProviderProperties(String baseUrl, String apiKey, String fromAddress) {
}
