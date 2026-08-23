package com.vortex.vortexweb.admin;

import com.vortex.vortexweb.booking.Appointment;
import com.vortex.vortexweb.booking.AppointmentRepository;
import com.vortex.vortexweb.booking.AppointmentStatus;
import com.vortex.vortexweb.notifications.NotificationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;

@Controller
@RequestMapping("/admin/appointments")
class AppointmentAdminController {

	private final AppointmentRepository appointmentRepository;

	private final NotificationService notificationService;

	@Autowired
	AppointmentAdminController(AppointmentRepository appointmentRepository, NotificationService notificationService) {
		this.appointmentRepository = appointmentRepository;
		this.notificationService = notificationService;
	}

	@GetMapping
	String appointments(Model model) {
		model.addAttribute("pendingAppointments",
				appointmentRepository.findByStatusIn(List.of(AppointmentStatus.PENDING)));
		model.addAttribute("confirmedAppointments",
				appointmentRepository.findByStatusIn(List.of(AppointmentStatus.CONFIRMED)));
		return "admin/appointments";
	}

	@PostMapping("/{id}/confirm")
	String confirm(@PathVariable Long id) {
		Appointment appointment = findOrNotFound(id);
		if (overlapsAnExistingConfirmedAppointment(appointment)) {
			throw new ResponseStatusException(HttpStatus.CONFLICT,
					"This slot now overlaps an existing confirmed appointment");
		}
		appointment.setStatus(AppointmentStatus.CONFIRMED);
		appointmentRepository.save(appointment);
		notificationService.send(appointment.getClientEmail(), "Your appointment is confirmed",
				"Your appointment on " + appointment.getStartTime() + " has been confirmed. See you then!");
		return "redirect:/admin/appointments";
	}

	@PostMapping("/{id}/decline")
	String decline(@PathVariable Long id) {
		Appointment appointment = findOrNotFound(id);
		appointment.setStatus(AppointmentStatus.DECLINED);
		appointmentRepository.save(appointment);
		notificationService.send(appointment.getClientEmail(), "Your appointment request was declined",
				"Unfortunately your request for " + appointment.getStartTime() + " has been declined.");
		return "redirect:/admin/appointments";
	}

	@PostMapping("/{id}/reschedule")
	String reschedule(@PathVariable Long id, @RequestParam LocalDateTime newStart) {
		Appointment appointment = findOrNotFound(id);
		LocalDateTime newEnd = newStart.plusMinutes(appointment.getDurationMinutes());
		if (overlapsAnotherConfirmedAppointment(id, newStart, newEnd)) {
			throw new ResponseStatusException(HttpStatus.CONFLICT,
					"This slot now overlaps an existing confirmed appointment");
		}
		appointment.setStartTime(newStart);
		appointmentRepository.save(appointment);
		notificationService.send(appointment.getClientEmail(), "Your appointment has been rescheduled",
				"Your appointment has been rescheduled to " + newStart + ".");
		return "redirect:/admin/appointments";
	}

	@PostMapping("/{id}/cancel")
	String cancel(@PathVariable Long id) {
		Appointment appointment = findOrNotFound(id);
		appointment.setStatus(AppointmentStatus.CANCELLED);
		appointmentRepository.save(appointment);
		notificationService.send(appointment.getClientEmail(), "Your appointment has been cancelled",
				"Your appointment on " + appointment.getStartTime() + " has been cancelled.");
		return "redirect:/admin/appointments";
	}

	@PostMapping("/{id}/complete")
	String complete(@PathVariable Long id) {
		Appointment appointment = findOrNotFound(id);
		appointment.setStatus(AppointmentStatus.COMPLETED);
		appointmentRepository.save(appointment);
		return "redirect:/admin/appointments";
	}

	private boolean overlapsAnExistingConfirmedAppointment(Appointment appointment) {
		LocalDateTime start = appointment.getStartTime();
		LocalDateTime end = start.plusMinutes(appointment.getDurationMinutes());
		return overlapsAnotherConfirmedAppointment(appointment.getId(), start, end);
	}

	private boolean overlapsAnotherConfirmedAppointment(Long excludeId, LocalDateTime start, LocalDateTime end) {
		return appointmentRepository.findByStatusIn(List.of(AppointmentStatus.CONFIRMED)).stream()
				.filter(existing -> !existing.getId().equals(excludeId))
				.anyMatch(existing -> {
					LocalDateTime existingStart = existing.getStartTime();
					LocalDateTime existingEnd = existingStart.plusMinutes(existing.getDurationMinutes());
					return start.isBefore(existingEnd) && existingStart.isBefore(end);
				});
	}

	private Appointment findOrNotFound(Long id) {
		return appointmentRepository.findById(id)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No such appointment"));
	}

}
