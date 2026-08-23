package com.vortex.vortexweb.booking;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
public class Appointment {

	@Id
	@GeneratedValue
	private Long id;

	private String clientName;

	private String clientEmail;

	private String clientPhone;

	private String description;

	private LocalDateTime startTime;

	private int durationMinutes;

	@Enumerated(EnumType.STRING)
	private AppointmentStatus status;

	public Appointment(String clientName, String clientEmail, String clientPhone, String description,
			LocalDateTime startTime, int durationMinutes, AppointmentStatus status) {
		this.clientName = clientName;
		this.clientEmail = clientEmail;
		this.clientPhone = clientPhone;
		this.description = description;
		this.startTime = startTime;
		this.durationMinutes = durationMinutes;
		this.status = status;
	}

}
