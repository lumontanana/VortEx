package com.vortex.vortexweb.admin;

import com.vortex.vortexweb.availability.AvailabilityRule;
import com.vortex.vortexweb.availability.AvailabilityRuleRepository;
import com.vortex.vortexweb.availability.BlockedPeriod;
import com.vortex.vortexweb.availability.BlockedPeriodRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Controller
@RequestMapping("/admin/availability")
class AvailabilityAdminController {

	private final AvailabilityRuleRepository availabilityRuleRepository;

	private final BlockedPeriodRepository blockedPeriodRepository;

	@Autowired
	AvailabilityAdminController(AvailabilityRuleRepository availabilityRuleRepository,
			BlockedPeriodRepository blockedPeriodRepository) {
		this.availabilityRuleRepository = availabilityRuleRepository;
		this.blockedPeriodRepository = blockedPeriodRepository;
	}

	@GetMapping
	String availability(Model model) {
		model.addAttribute("rules", availabilityRuleRepository.findAll());
		model.addAttribute("blockedPeriods", blockedPeriodRepository.findAll());
		return "admin/availability";
	}

	@PostMapping("/rules")
	String addRule(@RequestParam DayOfWeek dayOfWeek, @RequestParam LocalTime startTime,
			@RequestParam LocalTime endTime) {
		availabilityRuleRepository.save(new AvailabilityRule(dayOfWeek, startTime, endTime));
		return "redirect:/admin/availability";
	}

	@PostMapping("/rules/{id}/delete")
	String deleteRule(@PathVariable Long id) {
		availabilityRuleRepository.deleteById(id);
		return "redirect:/admin/availability";
	}

	@PostMapping("/blocked-periods")
	String addBlockedPeriod(@RequestParam LocalDateTime startDateTime, @RequestParam LocalDateTime endDateTime,
			@RequestParam(required = false) String reason) {
		blockedPeriodRepository.save(new BlockedPeriod(startDateTime, endDateTime, reason));
		return "redirect:/admin/availability";
	}

	@PostMapping("/blocked-periods/{id}/delete")
	String deleteBlockedPeriod(@PathVariable Long id) {
		blockedPeriodRepository.deleteById(id);
		return "redirect:/admin/availability";
	}

}
