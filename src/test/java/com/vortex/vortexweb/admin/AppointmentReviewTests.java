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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AppointmentReviewTests {

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
		emailProvider.stubFor(WireMock.post(WireMock.urlEqualTo("/emails")).willReturn(WireMock.aResponse().withStatus(200)));
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
	@SuppressWarnings("unchecked")
	void appointmentsPageListsPendingRequestsWithClientDetails() throws Exception {
		Appointment pending = appointmentRepository.save(new Appointment("Ada Lovelace", "ada@example.com",
				"555-1234", "A small geometric piece", LocalDateTime.now().plusDays(1), 60,
				AppointmentStatus.PENDING));
		MockHttpSession session = loginAsAdmin();

		MvcResult result = mockMvc.perform(get("/admin/appointments").session(session))
				.andExpect(status().isOk())
				.andReturn();

		var pendingAppointments = (java.util.List<Appointment>) result.getModelAndView().getModel()
				.get("pendingAppointments");
		assertThat(pendingAppointments).extracting(Appointment::getId).contains(pending.getId());
		Appointment shown = pendingAppointments.stream().filter(a -> a.getId().equals(pending.getId())).findFirst()
				.orElseThrow();
		assertThat(shown.getClientName()).isEqualTo("Ada Lovelace");
		assertThat(shown.getClientEmail()).isEqualTo("ada@example.com");
		assertThat(shown.getClientPhone()).isEqualTo("555-1234");
		assertThat(shown.getDescription()).isEqualTo("A small geometric piece");
	}

	@Test
	void unauthenticatedRequestToAppointmentsPageRedirectsToLogin() throws Exception {
		mockMvc.perform(get("/admin/appointments"))
				.andExpect(status().is3xxRedirection())
				.andExpect(redirectedUrl("/login"));
	}

	@Test
	void confirmingAPendingRequestTransitionsItAndSendsAConfirmationEmail() throws Exception {
		Appointment pending = appointmentRepository.save(new Appointment("Ada Lovelace", "ada@example.com",
				"555-1234", "A small geometric piece", LocalDateTime.now().plusDays(1), 60,
				AppointmentStatus.PENDING));
		MockHttpSession session = loginAsAdmin();

		mockMvc.perform(post("/admin/appointments/" + pending.getId() + "/confirm").session(session).with(csrf()))
				.andExpect(status().is3xxRedirection());

		assertThat(appointmentRepository.findById(pending.getId()).orElseThrow().getStatus())
				.isEqualTo(AppointmentStatus.CONFIRMED);
		emailProvider.verify(WireMock.postRequestedFor(WireMock.urlEqualTo("/emails"))
				.withRequestBody(WireMock.matchingJsonPath("$.to[0]", WireMock.equalTo("ada@example.com")))
				.withRequestBody(WireMock.matchingJsonPath("$.subject", WireMock.containing("confirmed"))));
	}

	@Test
	void confirmingIsRejectedWhenItOverlapsAnExistingConfirmedAppointment() throws Exception {
		LocalDateTime slotStart = LocalDateTime.now().plusDays(1).withHour(9).withMinute(0).withSecond(0).withNano(0);
		appointmentRepository.save(new Appointment("Grace Hopper", "grace@example.com", null, "A compass tattoo",
				slotStart, 60, AppointmentStatus.CONFIRMED));
		Appointment pending = appointmentRepository.save(new Appointment("Ada Lovelace", "ada@example.com",
				"555-1234", "A small geometric piece", slotStart, 60, AppointmentStatus.PENDING));
		MockHttpSession session = loginAsAdmin();

		mockMvc.perform(post("/admin/appointments/" + pending.getId() + "/confirm").session(session).with(csrf()))
				.andExpect(status().isConflict());

		assertThat(appointmentRepository.findById(pending.getId()).orElseThrow().getStatus())
				.isEqualTo(AppointmentStatus.PENDING);
	}

	@Test
	void decliningAPendingRequestTransitionsItAndSendsADeclineEmail() throws Exception {
		Appointment pending = appointmentRepository.save(new Appointment("Ada Lovelace", "ada@example.com",
				"555-1234", "A small geometric piece", LocalDateTime.now().plusDays(1), 60,
				AppointmentStatus.PENDING));
		MockHttpSession session = loginAsAdmin();

		mockMvc.perform(post("/admin/appointments/" + pending.getId() + "/decline").session(session).with(csrf()))
				.andExpect(status().is3xxRedirection());

		assertThat(appointmentRepository.findById(pending.getId()).orElseThrow().getStatus())
				.isEqualTo(AppointmentStatus.DECLINED);
		emailProvider.verify(WireMock.postRequestedFor(WireMock.urlEqualTo("/emails"))
				.withRequestBody(WireMock.matchingJsonPath("$.to[0]", WireMock.equalTo("ada@example.com")))
				.withRequestBody(WireMock.matchingJsonPath("$.subject", WireMock.containing("declined"))));
	}

}
