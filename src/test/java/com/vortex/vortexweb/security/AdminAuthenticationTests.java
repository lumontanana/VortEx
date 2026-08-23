package com.vortex.vortexweb.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.unauthenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AdminAuthenticationTests {

	@Autowired
	private MockMvc mockMvc;

	@Test
	void unauthenticatedRequestToAdminDashboardRedirectsToLogin() throws Exception {
		mockMvc.perform(get("/admin/dashboard"))
				.andExpect(status().is3xxRedirection())
				.andExpect(redirectedUrl("/login"));
	}

	@Test
	void loggingInWithCorrectCredentialsGrantsAccessToAdminDashboard() throws Exception {
		MvcResult loginResult = mockMvc.perform(formLogin().user("admin").password("admin"))
				.andExpect(authenticated())
				.andReturn();
		MockHttpSession session = (MockHttpSession) loginResult.getRequest().getSession();

		mockMvc.perform(get("/admin/dashboard").session(session))
				.andExpect(status().isOk());
	}

	@Test
	void loggingInWithIncorrectCredentialsIsRejected() throws Exception {
		MvcResult loginResult = mockMvc.perform(formLogin().user("admin").password("wrong-password"))
				.andExpect(unauthenticated())
				.andReturn();
		MockHttpSession session = (MockHttpSession) loginResult.getRequest().getSession();

		mockMvc.perform(get("/admin/dashboard").session(session))
				.andExpect(status().is3xxRedirection())
				.andExpect(redirectedUrl("/login"));
	}

}
