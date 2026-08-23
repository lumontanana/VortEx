package com.vortex.vortexweb.booking;

import java.time.LocalDateTime;

public record Slot(LocalDateTime start, LocalDateTime end) {
}
