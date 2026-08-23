package com.vortex.vortexweb.booking;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface AppointmentRepository extends JpaRepository<Appointment, Long> {

	List<Appointment> findByStatusIn(Collection<AppointmentStatus> statuses);

}
