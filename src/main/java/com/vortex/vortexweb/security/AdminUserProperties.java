package com.vortex.vortexweb.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "vortex.admin")
public record AdminUserProperties(String username, String password) {
}
