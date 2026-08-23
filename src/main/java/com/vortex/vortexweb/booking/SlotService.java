package com.vortex.vortexweb.booking;

import com.vortex.vortexweb.availability.AvailabilityRule;
import com.vortex.vortexweb.availability.AvailabilityRuleRepository;
import com.vortex.vortexweb.availability.BlockedPeriod;
import com.vortex.vortexweb.availability.BlockedPeriodRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class SlotService {

	private static final int LOOKAHEAD_DAYS = 30;

	private final AvailabilityRuleRepository availabilityRuleRepository;

	private final BlockedPeriodRepository blockedPeriodRepository;

	private final AppointmentRepository appointmentRepository;

	private final BookingProperties bookingProperties;

	@Autowired
	public SlotService(AvailabilityRuleRepository availabilityRuleRepository,
			BlockedPeriodRepository blockedPeriodRepository, AppointmentRepository appointmentRepository,
			BookingProperties bookingProperties) {
		this.availabilityRuleRepository = availabilityRuleRepository;
		this.blockedPeriodRepository = blockedPeriodRepository;
		this.appointmentRepository = appointmentRepository;
		this.bookingProperties = bookingProperties;
	}

	public List<Slot> openSlots() {
		List<AvailabilityRule> rules = availabilityRuleRepository.findAll();
		List<BlockedPeriod> blockedPeriods = blockedPeriodRepository.findAll();
		Set<LocalDateTime> takenStarts = appointmentRepository
				.findByStatusIn(List.of(AppointmentStatus.PENDING, AppointmentStatus.CONFIRMED)).stream()
				.map(Appointment::getStartTime)
				.collect(Collectors.toSet());
		LocalDateTime now = LocalDateTime.now();
		LocalDate today = now.toLocalDate();
		int durationMinutes = bookingProperties.defaultDurationMinutes();

		List<Slot> slots = new ArrayList<>();
		for (LocalDate date = today; !date.isAfter(today.plusDays(LOOKAHEAD_DAYS)); date = date.plusDays(1)) {
			for (AvailabilityRule rule : rules) {
				if (rule.getDayOfWeek() != date.getDayOfWeek()) {
					continue;
				}
				LocalDateTime slotStart = LocalDateTime.of(date, rule.getStartTime());
				LocalDateTime ruleEnd = LocalDateTime.of(date, rule.getEndTime());
				while (!slotStart.plusMinutes(durationMinutes).isAfter(ruleEnd)) {
					LocalDateTime candidateStart = slotStart;
					LocalDateTime candidateEnd = slotStart.plusMinutes(durationMinutes);
					if (!candidateStart.isBefore(now) && !takenStarts.contains(candidateStart)
							&& blockedPeriods.stream()
									.noneMatch(blocked -> overlaps(candidateStart, candidateEnd, blocked))) {
						slots.add(new Slot(candidateStart, candidateEnd));
					}
					slotStart = candidateEnd;
				}
			}
		}
		slots.sort((a, b) -> a.start().compareTo(b.start()));
		return slots;
	}

	public boolean isOpen(LocalDateTime slotStart) {
		return openSlots().stream().anyMatch(slot -> slot.start().equals(slotStart));
	}

	private boolean overlaps(LocalDateTime start, LocalDateTime end, BlockedPeriod blockedPeriod) {
		return start.isBefore(blockedPeriod.getEndDateTime()) && blockedPeriod.getStartDateTime().isBefore(end);
	}

}
