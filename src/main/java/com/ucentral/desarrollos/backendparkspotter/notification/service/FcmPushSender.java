package com.ucentral.desarrollos.backendparkspotter.notification.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.util.Map;

/**
 * Push con Firebase Cloud Messaging (API HTTP v1). Sirve para la app Android y para la web
 * (el navegador registra su token con el SDK web de Firebase).
 */
@Slf4j
public class FcmPushSender implements PushSender {

    private final RestClient restClient;
    private final GoogleAccessTokenProvider tokenProvider;
    private final String sendUrl;

    public FcmPushSender(RestClient restClient, GoogleAccessTokenProvider tokenProvider, String projectId) {
        this.restClient = restClient;
        this.tokenProvider = tokenProvider;
        this.sendUrl = "https://fcm.googleapis.com/v1/projects/" + projectId + "/messages:send";
    }

    @Override
    public Result send(String deviceToken, String title, String body, Map<String, String> data) {
        Map<String, Object> message = Map.of("message", Map.of(
                "token", deviceToken,
                "notification", Map.of("title", title, "body", body),
                "data", data));
        try {
            restClient.post()
                    .uri(sendUrl)
                    .header("Authorization", "Bearer " + tokenProvider.accessToken())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(message)
                    .retrieve()
                    .toBodilessEntity();
            return Result.SENT;
        } catch (RestClientResponseException ex) {
            // 404 UNREGISTERED / 400 INVALID_ARGUMENT: el token ya no sirve y se debe borrar.
            HttpStatusCode status = ex.getStatusCode();
            if (status.value() == 404 || (status.value() == 400 && ex.getResponseBodyAsString().contains("INVALID_ARGUMENT"))) {
                return Result.INVALID_TOKEN;
            }
            log.warn("FCM respondió {} al enviar push: {}", status.value(), ex.getMessage());
            return Result.FAILED;
        } catch (RestClientException | IllegalStateException ex) {
            log.warn("No se pudo enviar el push: {}", ex.getMessage());
            return Result.FAILED;
        }
    }
}
