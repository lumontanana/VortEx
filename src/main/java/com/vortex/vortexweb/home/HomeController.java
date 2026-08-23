package com.vortex.vortexweb.home;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
class HomeController {

	private final ArtistProfileProperties artistProfileProperties;

	@Autowired
	HomeController(ArtistProfileProperties artistProfileProperties) {
		this.artistProfileProperties = artistProfileProperties;
	}

	@GetMapping("/")
	String home(Model model) {
		model.addAttribute("artist", artistProfileProperties);
		return "home";
	}

}
