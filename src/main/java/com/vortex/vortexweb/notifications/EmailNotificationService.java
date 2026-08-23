package com.vortex.vortexweb.notifications;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.List;

/**
 * Sends email via the Resend API (https://resend.com/docs/api-reference/emails/send-email).
 */
@Service
class EmailNotificationService implements NotificationService {

	private static final Logger log = LoggerFactory.getLogger(EmailNotificationService.class);

	private final RestClient restClient;

	private final EmailProviderProperties properties;

	EmailNotificationService(RestClient.Builder restClientBuilder, EmailProviderProperties properties) {
		this.restClient = restClientBuilder.baseUrl(properties.baseUrl()).build();
		this.properties = properties;
	}

	@Override
	public void send(String to, String subject, String body) {
		try {
			restClient.post()
					.uri("/emails")
					.header("Authorization", "Bearer " + properties.apiKey())
					.contentType(MediaType.APPLICATION_JSON)
					.body(new EmailRequest(properties.fromAddress(), List.of(to), subject, body))
					.retrieve()
					.toBodilessEntity();
		}
		catch (Exception e) {
			log.error("Failed to send email to {} (subject: {})", to, subject, e);
		}
	}

	private record EmailRequest(String from, List<String> to, String subject, String text) {
	}

}
