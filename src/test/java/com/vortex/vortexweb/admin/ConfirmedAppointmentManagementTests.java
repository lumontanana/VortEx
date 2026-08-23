package com.vortex.vortexweb.admin;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.vortex.vortexweb.booking.Appointment;
import com.vortex.vortexweb.booking.AppointmentRepository;
import com.vortex.vortexweb.booking.AppointmentStatus;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ConfirmedAppointmentManagementTests {

	private static final WireMockServer emailProvider = new WireMockServer(WireMockConfiguration.options().dynamicPort());

	static {
		emailProvider.start();
	}

	@DynamicPropertySource
	static void emailProviderProperties(DynamicPropertyRegistry registry) {
		registry.add("vortex.notifications.email.base-url", emailProvider::baseUrl);
		// JDK HttpClient negotiates HTTP/2 against WireMock's embedded Jetty and gets RST_STREAM'd; force HTTP/1.1.
		registry.add("spring.http.clients.imperative.factory", () -> "simple");
	}

	@AfterAll
	static void stopEmailProvider() {
		emailProvider.stop();
	}

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private AppointmentRepository appointmentRepository;

	@BeforeEach
	void stubEmailProvider() {
		emailProvider.stubFor(WireMock.post(WireMock.urlEqualTo("/send")).willReturn(WireMock.aResponse().withStatus(200)));
	}

	@AfterEach
	void cleanUp() {
		emailProvider.resetAll();
		appointmentRepository.deleteAll();
	}

	private MockHttpSession loginAsAdmin() throws Exception {
		MvcResult loginResult = mockMvc.perform(formLogin().user("admin").password("admin"))
				.andExpect(authenticated())
				.andReturn();
		return (MockHttpSession) loginResult.getRequest().getSession();
	}

	@Test
	void reschedulingAConfirmedAppointmentMovesItAndSendsAnEmail() throws Exception {
		LocalDateTime originalStart = LocalDateTime.now().plusDays(1).withHour(9).withMinute(0).withSecond(0)
				.withNano(0);
		LocalDateTime newStart = originalStart.plusDays(1);
		Appointment confirmed = appointmentRepository.save(new Appointment("Ada Lovelace", "ada@example.com",
				"555-1234", "A small geometric piece", originalStart, 60, AppointmentStatus.CONFIRMED));
		MockHttpSession session = loginAsAdmin();

		mockMvc.perform(post("/admin/appointments/" + confirmed.getId() + "/reschedule").session(session)
						.param("newStart", newStart.toString())
						.with(csrf()))
				.andExpect(status().is3xxRedirection());

		Appointment reloaded = appointmentRepository.findById(confirmed.getId()).orElseThrow();
		assertThat(reloaded.getStartTime()).isEqualTo(newStart);
		assertThat(reloaded.getStatus()).isEqualTo(AppointmentStatus.CONFIRMED);
		emailProvider.verify(WireMock.postRequestedFor(WireMock.urlEqualTo("/send"))
				.withRequestBody(WireMock.matchingJsonPath("$.to", WireMock.equalTo("ada@example.com")))
				.withRequestBody(WireMock.matchingJsonPath("$.subject", WireMock.containing("reschedul"))));
	}

	@Test
	void reschedulingIntoAnOverlappingConfirmedAppointmentIsRejected() throws Exception {
		LocalDateTime originalStart = LocalDateTime.now().plusDays(1).withHour(9).withMinute(0).withSecond(0)
				.withNano(0);
		LocalDateTime otherStart = originalStart.plusDays(1);
		Appointment confirmed = appointmentRepository.save(new Appointment("Ada Lovelace", "ada@example.com",
				"555-1234", "A small geometric piece", originalStart, 60, AppointmentStatus.CONFIRMED));
		appointmentRepository.save(new Appointment("Grace Hopper", "grace@example.com", null, "A compass tattoo",
				otherStart, 60, AppointmentStatus.CONFIRMED));
		MockHttpSession session = loginAsAdmin();

		mockMvc.perform(post("/admin/appointments/" + confirmed.getId() + "/reschedule").session(session)
						.param("newStart", otherStart.toString())
						.with(csrf()))
				.andExpect(status().isConflict());

		assertThat(appointmentRepository.findById(confirmed.getId()).orElseThrow().getStartTime())
				.isEqualTo(originalStart);
	}

	@Test
	void cancellingAConfirmedAppointmentTransitionsItAndSendsAnEmail() throws Exception {
		Appointment confirmed = appointmentRepository.save(new Appointment("Ada Lovelace", "ada@example.com",
				"555-1234", "A small geometric piece", LocalDateTime.now().plusDays(1), 60,
				AppointmentStatus.CONFIRMED));
		MockHttpSession session = loginAsAdmin();

		mockMvc.perform(post("/admin/appointments/" + confirmed.getId() + "/cancel").session(session).with(csrf()))
				.andExpect(status().is3xxRedirection());

		assertThat(appointmentRepository.findById(confirmed.getId()).orElseThrow().getStatus())
				.isEqualTo(AppointmentStatus.CANCELLED);
		emailProvider.verify(WireMock.postRequestedFor(WireMock.urlEqualTo("/send"))
				.withRequestBody(WireMock.matchingJsonPath("$.to", WireMock.equalTo("ada@example.com")))
				.withRequestBody(WireMock.matchingJsonPath("$.subject", WireMock.containing("cancel"))));
	}

	@Test
	void markingAConfirmedAppointmentCompleteTransitionsItWithoutSendingAnEmail() throws Exception {
		Appointment confirmed = appointmentRepository.save(new Appointment("Ada Lovelace", "ada@example.com",
				"555-1234", "A small geometric piece", LocalDateTime.now().minusDays(1), 60,
				AppointmentStatus.CONFIRMED));
		MockHttpSession session = loginAsAdmin();

		mockMvc.perform(post("/admin/appointments/" + confirmed.getId() + "/complete").session(session).with(csrf()))
				.andExpect(status().is3xxRedirection());

		assertThat(appointmentRepository.findById(confirmed.getId()).orElseThrow().getStatus())
				.isEqualTo(AppointmentStatus.COMPLETED);
		emailProvider.verify(0, WireMock.postRequestedFor(WireMock.urlEqualTo("/send")));
	}

	@Test
	void unauthenticatedAccessToConfirmedAppointmentActionsIsRejected() throws Exception {
		Appointment confirmed = appointmentRepository.save(new Appointment("Ada Lovelace", "ada@example.com",
				"555-1234", "A small geometric piece", LocalDateTime.now().plusDays(1), 60,
				AppointmentStatus.CONFIRMED));

		mockMvc.perform(post("/admin/appointments/" + confirmed.getId() + "/cancel").with(csrf()))
				.andExpect(status().is3xxRedirection())
				.andExpect(redirectedUrl("/login"));
	}

}
