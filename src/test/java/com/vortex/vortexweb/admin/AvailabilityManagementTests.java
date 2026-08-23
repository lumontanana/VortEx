package com.vortex.vortexweb.admin;

import com.vortex.vortexweb.availability.AvailabilityRule;
import com.vortex.vortexweb.availability.AvailabilityRuleRepository;
import com.vortex.vortexweb.availability.BlockedPeriod;
import com.vortex.vortexweb.availability.BlockedPeriodRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

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
class AvailabilityManagementTests {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private AvailabilityRuleRepository availabilityRuleRepository;

	@Autowired
	private BlockedPeriodRepository blockedPeriodRepository;

	@AfterEach
	void cleanUp() {
		availabilityRuleRepository.deleteAll();
		blockedPeriodRepository.deleteAll();
	}

	private MockHttpSession loginAsAdmin() throws Exception {
		MvcResult loginResult = mockMvc.perform(formLogin().user("admin").password("admin"))
				.andExpect(authenticated())
				.andReturn();
		return (MockHttpSession) loginResult.getRequest().getSession();
	}

	@Test
	void artistCanAddAnAvailabilityRule() throws Exception {
		MockHttpSession session = loginAsAdmin();

		mockMvc.perform(post("/admin/availability/rules")
						.session(session)
						.param("dayOfWeek", "MONDAY")
						.param("startTime", "09:00")
						.param("endTime", "17:00")
						.with(csrf()))
				.andExpect(status().is3xxRedirection());

		List<AvailabilityRule> rules = availabilityRuleRepository.findAll();
		assertThat(rules).hasSize(1);
		assertThat(rules.get(0).getDayOfWeek()).isEqualTo(DayOfWeek.MONDAY);
		assertThat(rules.get(0).getStartTime()).isEqualTo(LocalTime.of(9, 0));
		assertThat(rules.get(0).getEndTime()).isEqualTo(LocalTime.of(17, 0));
	}

	@Test
	void artistCanRemoveAnAvailabilityRule() throws Exception {
		AvailabilityRule rule = availabilityRuleRepository
				.save(new AvailabilityRule(DayOfWeek.TUESDAY, LocalTime.of(10, 0), LocalTime.of(14, 0)));
		MockHttpSession session = loginAsAdmin();

		mockMvc.perform(post("/admin/availability/rules/" + rule.getId() + "/delete")
						.session(session)
						.with(csrf()))
				.andExpect(status().is3xxRedirection());

		assertThat(availabilityRuleRepository.findById(rule.getId())).isEmpty();
	}

	@Test
	void artistCanAddABlockedPeriod() throws Exception {
		MockHttpSession session = loginAsAdmin();

		mockMvc.perform(post("/admin/availability/blocked-periods")
						.session(session)
						.param("startDateTime", "2026-09-01T00:00:00")
						.param("endDateTime", "2026-09-07T23:59:59")
						.param("reason", "Vacation")
						.with(csrf()))
				.andExpect(status().is3xxRedirection());

		List<BlockedPeriod> blockedPeriods = blockedPeriodRepository.findAll();
		assertThat(blockedPeriods).hasSize(1);
		assertThat(blockedPeriods.get(0).getStartDateTime()).isEqualTo(LocalDateTime.of(2026, 9, 1, 0, 0, 0));
		assertThat(blockedPeriods.get(0).getEndDateTime()).isEqualTo(LocalDateTime.of(2026, 9, 7, 23, 59, 59));
		assertThat(blockedPeriods.get(0).getReason()).isEqualTo("Vacation");
	}

	@Test
	void artistCanRemoveABlockedPeriod() throws Exception {
		BlockedPeriod blockedPeriod = blockedPeriodRepository.save(new BlockedPeriod(
				LocalDateTime.of(2026, 10, 1, 0, 0), LocalDateTime.of(2026, 10, 3, 0, 0), "Personal"));
		MockHttpSession session = loginAsAdmin();

		mockMvc.perform(post("/admin/availability/blocked-periods/" + blockedPeriod.getId() + "/delete")
						.session(session)
						.with(csrf()))
				.andExpect(status().is3xxRedirection());

		assertThat(blockedPeriodRepository.findById(blockedPeriod.getId())).isEmpty();
	}

	@Test
	void unauthenticatedRequestToAvailabilityPageRedirectsToLogin() throws Exception {
		mockMvc.perform(get("/admin/availability"))
				.andExpect(status().is3xxRedirection())
				.andExpect(redirectedUrl("/login"));
	}

}
