package com.vortex.vortexweb.home;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "vortex.artist")
public record ArtistProfileProperties(String name, String bio, String instagramUrl, String email) {
}
