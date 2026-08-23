package com.vortex.vortexweb.booking;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "vortex.booking")
public record BookingProperties(int defaultDurationMinutes) {
}
