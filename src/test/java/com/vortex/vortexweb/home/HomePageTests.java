package com.vortex.vortexweb.home;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class HomePageTests {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ArtistProfileProperties artistProfileProperties;

	@Test
	void homePageRendersArtistProfileAndLinksToBooking() throws Exception {
		mockMvc.perform(get("/"))
				.andExpect(status().isOk())
				.andExpect(content().string(org.hamcrest.Matchers.containsString(artistProfileProperties.name())))
				.andExpect(content().string(org.hamcrest.Matchers.containsString(artistProfileProperties.bio())))
				.andExpect(content().string(org.hamcrest.Matchers.containsString(artistProfileProperties.email())))
				.andExpect(content().string(org.hamcrest.Matchers.containsString("/booking")));
	}

}
