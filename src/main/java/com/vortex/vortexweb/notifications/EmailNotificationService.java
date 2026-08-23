package com.vortex.vortexweb.notifications;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
class EmailNotificationService implements NotificationService {

	private final RestClient restClient;

	private final EmailProviderProperties properties;

	EmailNotificationService(RestClient.Builder restClientBuilder, EmailProviderProperties properties) {
		this.restClient = restClientBuilder.baseUrl(properties.baseUrl()).build();
		this.properties = properties;
	}

	@Override
	public void send(String to, String subject, String body) {
		restClient.post()
				.uri("/send")
				.header("Authorization", "Bearer " + properties.apiKey())
				.contentType(MediaType.APPLICATION_JSON)
				.body(new EmailRequest(properties.fromAddress(), to, subject, body))
				.retrieve()
				.toBodilessEntity();
	}

	private record EmailRequest(String from, String to, String subject, String body) {
	}

}
