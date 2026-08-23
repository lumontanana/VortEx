package com.vortex.vortexweb.booking;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import java.time.LocalDateTime;

@Controller
@RequestMapping("/booking")
class BookingController {

	private final SlotService slotService;

	private final AppointmentRepository appointmentRepository;

	private final BookingProperties bookingProperties;

	@Autowired
	BookingController(SlotService slotService, AppointmentRepository appointmentRepository,
			BookingProperties bookingProperties) {
		this.slotService = slotService;
		this.appointmentRepository = appointmentRepository;
		this.bookingProperties = bookingProperties;
	}

	@GetMapping
	String booking(Model model) {
		model.addAttribute("slots", slotService.openSlots());
		return "booking";
	}

	@PostMapping
	String submit(@RequestParam LocalDateTime slotStart, @RequestParam String clientName,
			@RequestParam String clientEmail, @RequestParam(required = false) String clientPhone,
			@RequestParam String description) {
		if (!slotService.isOpen(slotStart)) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "This slot is no longer available");
		}
		appointmentRepository.save(new Appointment(clientName, clientEmail, clientPhone, description, slotStart,
				bookingProperties.defaultDurationMinutes(), AppointmentStatus.PENDING));
		return "redirect:/booking";
	}

}
