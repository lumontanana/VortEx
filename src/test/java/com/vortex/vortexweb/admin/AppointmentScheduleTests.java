package com.vortex.vortexweb.admin;

import com.vortex.vortexweb.booking.Appointment;
import com.vortex.vortexweb.booking.AppointmentRepository;
import com.vortex.vortexweb.booking.AppointmentStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AppointmentScheduleTests {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private AppointmentRepository appointmentRepository;

	@AfterEach
	void cleanUp() {
		appointmentRepository.deleteAll();
	}

	private MockHttpSession loginAsAdmin() throws Exception {
		MvcResult loginResult = mockMvc.perform(formLogin().user("admin").password("admin"))
				.andExpect(authenticated())
				.andReturn();
		return (MockHttpSession) loginResult.getRequest().getSession();
	}

	@SuppressWarnings("unchecked")
	private List<Appointment> attribute(MvcResult result, String name) {
		return (List<Appointment>) result.getModelAndView().getModel().get(name);
	}

	@Test
	void scheduleListsOnlyUpcomingConfirmedAppointments() throws Exception {
		Appointment upcoming = appointmentRepository.save(new Appointment("Ada Lovelace", "ada@example.com",
				"555-1234", "A small geometric piece", LocalDateTime.now().plusDays(1), 60,
				AppointmentStatus.CONFIRMED));
		Appointment past = appointmentRepository.save(new Appointment("Grace Hopper", "grace@example.com", null,
				"A compass tattoo", LocalDateTime.now().minusDays(1), 60, AppointmentStatus.CONFIRMED));
		MockHttpSession session = loginAsAdmin();

		MvcResult result = mockMvc.perform(get("/admin/appointments/schedule").session(session))
				.andExpect(status().isOk())
				.andReturn();

		List<Appointment> upcomingAppointments = attribute(result, "upcomingAppointments");
		assertThat(upcomingAppointments).extracting(Appointment::getId).contains(upcoming.getId())
				.doesNotContain(past.getId());
	}

	@Test
	void scheduleListsCompletedDeclinedAndCancelledAppointmentsAsHistory() throws Exception {
		Appointment completed = appointmentRepository.save(new Appointment("Ada Lovelace", "ada@example.com", null,
				"A small geometric piece", LocalDateTime.now().minusDays(5), 60, AppointmentStatus.COMPLETED));
		Appointment declined = appointmentRepository.save(new Appointment("Grace Hopper", "grace@example.com", null,
				"A compass tattoo", LocalDateTime.now().minusDays(4), 60, AppointmentStatus.DECLINED));
		Appointment cancelled = appointmentRepository.save(new Appointment("Alan Turing", "alan@example.com", null,
				"A machine tattoo", LocalDateTime.now().minusDays(3), 60, AppointmentStatus.CANCELLED));
		Appointment stillPending = appointmentRepository.save(new Appointment("Margaret Hamilton",
				"margaret@example.com", null, "A rocket tattoo", LocalDateTime.now().plusDays(1), 60,
				AppointmentStatus.PENDING));
		MockHttpSession session = loginAsAdmin();

		MvcResult result = mockMvc.perform(get("/admin/appointments/schedule").session(session))
				.andExpect(status().isOk())
				.andReturn();

		List<Appointment> pastAppointments = attribute(result, "pastAppointments");
		assertThat(pastAppointments).extracting(Appointment::getId)
				.contains(completed.getId(), declined.getId(), cancelled.getId())
				.doesNotContain(stillPending.getId());
	}

	@Test
	void unauthenticatedRequestToSchedulePageRedirectsToLogin() throws Exception {
		mockMvc.perform(get("/admin/appointments/schedule"))
				.andExpect(status().is3xxRedirection())
				.andExpect(redirectedUrl("/login"));
	}

}
