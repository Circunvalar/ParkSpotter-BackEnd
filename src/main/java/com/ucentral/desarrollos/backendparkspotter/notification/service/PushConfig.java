package com.ucentral.desarrollos.backendparkspotter.notification.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

import java.time.Clock;

/**
 * Push real solo si app.push.fcm.enabled=true y están las credenciales de la cuenta de servicio
 * (variables FCM_PROJECT_ID, FCM_CLIENT_EMAIL y FCM_PRIVATE_KEY). Si no, los push solo se registran en el log.
 */
@Slf4j
@Configuration
public class PushConfig {

    @Bean
    public PushSender pushSender(@Value("${app.push.fcm.enabled:false}") boolean enabled,
                                 @Value("${app.push.fcm.project-id:}") String projectId,
                                 @Value("${app.push.fcm.client-email:}") String clientEmail,
                                 @Value("${app.push.fcm.private-key:}") String privateKey,
                                 @Value("${app.push.fcm.token-uri:https://oauth2.googleapis.com/token}") String tokenUri,
                                 Clock clock) {
        if (!enabled || projectId.isBlank() || clientEmail.isBlank() || privateKey.isBlank()) {
            return (token, title, body, data) -> {
                log.debug("Push deshabilitado: {}", title);
                return PushSender.Result.DISABLED;
            };
        }
        RestClient restClient = RestClient.create();
        return new FcmPushSender(restClient, new GoogleAccessTokenProvider(restClient, clientEmail, privateKey, tokenUri, clock), projectId);
    }
}
