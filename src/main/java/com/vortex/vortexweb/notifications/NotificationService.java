package com.vortex.vortexweb.notifications;

public interface NotificationService {

	void send(String to, String subject, String body);

}
