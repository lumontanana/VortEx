package com.vortex.vortexweb.booking;

import com.vortex.vortexweb.availability.AvailabilityRule;
import com.vortex.vortexweb.availability.AvailabilityRuleRepository;
import com.vortex.vortexweb.availability.BlockedPeriod;
import com.vortex.vortexweb.availability.BlockedPeriodRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class BookingTests {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private AvailabilityRuleRepository availabilityRuleRepository;

	@Autowired
	private AppointmentRepository appointmentRepository;

	@Autowired
	private BlockedPeriodRepository blockedPeriodRepository;

	@AfterEach
	void cleanUp() {
		appointmentRepository.deleteAll();
		availabilityRuleRepository.deleteAll();
		blockedPeriodRepository.deleteAll();
	}

	@Test
	void openSlotsPageListsASlotGeneratedFromAnAvailabilityRule() throws Exception {
		LocalDate tomorrow = LocalDate.now().plusDays(1);
		availabilityRuleRepository
				.save(new AvailabilityRule(tomorrow.getDayOfWeek(), LocalTime.of(9, 0), LocalTime.of(10, 0)));

		mockMvc.perform(get("/booking"))
				.andExpect(status().isOk())
				.andExpect(model().attribute("slots",
						hasItem(new Slot(tomorrow.atTime(9, 0), tomorrow.atTime(10, 0)))));
	}

	@Test
	void submittingARequestAgainstAnOpenSlotCreatesAPendingAppointmentAndClosesTheSlot() throws Exception {
		LocalDate tomorrow = LocalDate.now().plusDays(1);
		availabilityRuleRepository
				.save(new AvailabilityRule(tomorrow.getDayOfWeek(), LocalTime.of(9, 0), LocalTime.of(10, 0)));
		LocalDateTime slotStart = tomorrow.atTime(9, 0);

		mockMvc.perform(post("/booking")
						.param("slotStart", slotStart.toString())
						.param("clientName", "Ada Lovelace")
						.param("clientEmail", "ada@example.com")
						.param("clientPhone", "555-1234")
						.param("description", "A small geometric piece")
						.with(csrf()))
				.andExpect(status().is3xxRedirection());

		List<Appointment> appointments = appointmentRepository.findAll();
		assertThat(appointments).hasSize(1);
		Appointment appointment = appointments.get(0);
		assertThat(appointment.getClientName()).isEqualTo("Ada Lovelace");
		assertThat(appointment.getClientEmail()).isEqualTo("ada@example.com");
		assertThat(appointment.getClientPhone()).isEqualTo("555-1234");
		assertThat(appointment.getDescription()).isEqualTo("A small geometric piece");
		assertThat(appointment.getStartTime()).isEqualTo(slotStart);
		assertThat(appointment.getStatus()).isEqualTo(AppointmentStatus.PENDING);

		mockMvc.perform(get("/booking"))
				.andExpect(model().attribute("slots", not(hasItem(new Slot(slotStart, slotStart.plusHours(1))))));
	}

	@Test
	void submittingARequestAgainstAnAlreadyTakenSlotIsRejected() throws Exception {
		LocalDate tomorrow = LocalDate.now().plusDays(1);
		availabilityRuleRepository
				.save(new AvailabilityRule(tomorrow.getDayOfWeek(), LocalTime.of(9, 0), LocalTime.of(10, 0)));
		LocalDateTime slotStart = tomorrow.atTime(9, 0);
		appointmentRepository.save(new Appointment("Grace Hopper", "grace@example.com", null, "A compass tattoo",
				slotStart, 60, AppointmentStatus.PENDING));

		mockMvc.perform(post("/booking")
						.param("slotStart", slotStart.toString())
						.param("clientName", "Ada Lovelace")
						.param("clientEmail", "ada@example.com")
						.param("description", "A small geometric piece")
						.with(csrf()))
				.andExpect(status().isConflict());

		assertThat(appointmentRepository.findAll()).hasSize(1);
	}

	@Test
	void submittingARequestAgainstABlockedSlotIsRejected() throws Exception {
		LocalDate tomorrow = LocalDate.now().plusDays(1);
		availabilityRuleRepository
				.save(new AvailabilityRule(tomorrow.getDayOfWeek(), LocalTime.of(9, 0), LocalTime.of(10, 0)));
		LocalDateTime slotStart = tomorrow.atTime(9, 0);
		blockedPeriodRepository.save(new BlockedPeriod(tomorrow.atStartOfDay(), tomorrow.plusDays(1).atStartOfDay(),
				"Day off"));

		mockMvc.perform(post("/booking")
						.param("slotStart", slotStart.toString())
						.param("clientName", "Ada Lovelace")
						.param("clientEmail", "ada@example.com")
						.param("description", "A small geometric piece")
						.with(csrf()))
				.andExpect(status().isConflict());

		assertThat(appointmentRepository.findAll()).isEmpty();
	}

}
