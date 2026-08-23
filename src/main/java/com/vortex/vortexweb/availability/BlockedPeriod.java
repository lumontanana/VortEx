package com.vortex.vortexweb.availability;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Getter
@Setter
@NoArgsConstructor
public class BlockedPeriod {

	@Id
	@GeneratedValue
	private Long id;

	private LocalDateTime startDateTime;

	private LocalDateTime endDateTime;

	private String reason;

	public BlockedPeriod(LocalDateTime startDateTime, LocalDateTime endDateTime, String reason) {
		this.startDateTime = startDateTime;
		this.endDateTime = endDateTime;
		this.reason = reason;
	}

}
