package com.vortex.vortexweb.booking;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.vortex.vortexweb.availability.AvailabilityRule;
import com.vortex.vortexweb.availability.AvailabilityRuleRepository;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class BookingNotificationTests {

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
	private AvailabilityRuleRepository availabilityRuleRepository;

	@Autowired
	private AppointmentRepository appointmentRepository;

	@BeforeEach
	void stubEmailProvider() {
		emailProvider.stubFor(WireMock.post(WireMock.urlEqualTo("/emails"))
				.willReturn(WireMock.aResponse().withStatus(200)));
	}

	@AfterEach
	void cleanUp() {
		emailProvider.resetAll();
		appointmentRepository.deleteAll();
		availabilityRuleRepository.deleteAll();
	}

	@Test
	void submittingABookingRequestSendsARequestReceivedEmailToTheClient() throws Exception {
		LocalDate tomorrow = LocalDate.now().plusDays(1);
		availabilityRuleRepository
				.save(new AvailabilityRule(tomorrow.getDayOfWeek(), LocalTime.of(9, 0), LocalTime.of(10, 0)));
		LocalDateTime slotStart = tomorrow.atTime(9, 0);

		mockMvc.perform(post("/booking")
						.param("slotStart", slotStart.toString())
						.param("clientName", "Ada Lovelace")
						.param("clientEmail", "ada@example.com")
						.param("description", "A small geometric piece")
						.with(csrf()))
				.andExpect(status().is3xxRedirection());

		emailProvider.verify(WireMock.postRequestedFor(WireMock.urlEqualTo("/emails"))
				.withRequestBody(WireMock.matchingJsonPath("$.to[0]", WireMock.equalTo("ada@example.com")))
				.withRequestBody(WireMock.matchingJsonPath("$.subject", WireMock.containing("request")))
				.withRequestBody(WireMock.matchingJsonPath("$.text", WireMock.containing("A small geometric piece"))));
	}

	@Test
	void submittingABookingRequestStillSucceedsWhenTheEmailProviderFails() throws Exception {
		emailProvider.resetAll();
		emailProvider.stubFor(WireMock.post(WireMock.urlEqualTo("/emails"))
				.willReturn(WireMock.aResponse().withStatus(500)));
		LocalDate tomorrow = LocalDate.now().plusDays(1);
		availabilityRuleRepository
				.save(new AvailabilityRule(tomorrow.getDayOfWeek(), LocalTime.of(9, 0), LocalTime.of(10, 0)));
		LocalDateTime slotStart = tomorrow.atTime(9, 0);

		mockMvc.perform(post("/booking")
						.param("slotStart", slotStart.toString())
						.param("clientName", "Ada Lovelace")
						.param("clientEmail", "ada@example.com")
						.param("description", "A small geometric piece")
						.with(csrf()))
				.andExpect(status().is3xxRedirection());

		assertThat(appointmentRepository.findAll()).hasSize(1);
	}

}
