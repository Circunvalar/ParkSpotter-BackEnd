package com.ucentral.desarrollos.backendparkspotter.notification.service;

import io.jsonwebtoken.Jwts;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;

import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;

/**
 * Obtiene el access token OAuth2 de la cuenta de servicio de Firebase (flujo JWT bearer de Google),
 * sin depender del SDK de Firebase. El token se reutiliza hasta un minuto antes de vencer.
 */
public class GoogleAccessTokenProvider {

    static final String SCOPE = "https://www.googleapis.com/auth/firebase.messaging";

    private final RestClient restClient;
    private final String clientEmail;
    private final PrivateKey privateKey;
    private final String tokenUri;
    private final Clock clock;

    private String cachedToken;
    private Instant cachedUntil = Instant.EPOCH;

    public GoogleAccessTokenProvider(RestClient restClient, String clientEmail, String privateKeyPem, String tokenUri, Clock clock) {
        this.restClient = restClient;
        this.clientEmail = clientEmail;
        this.privateKey = parsePrivateKey(privateKeyPem);
        this.tokenUri = tokenUri;
        this.clock = clock;
    }

    public synchronized String accessToken() {
        Instant now = Instant.now(clock);
        if (cachedToken != null && now.isBefore(cachedUntil)) {
            return cachedToken;
        }
        String assertion = Jwts.builder()
                .issuer(clientEmail)
                .audience().add(tokenUri).and()
                .claim("scope", SCOPE)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(Duration.ofHours(1))))
                .signWith(privateKey, Jwts.SIG.RS256)
                .compact();
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "urn:ietf:params:oauth:grant-type:jwt-bearer");
        form.add("assertion", assertion);
        JsonNode response = restClient.post()
                .uri(tokenUri)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .body(JsonNode.class);
        if (response == null || !response.hasNonNull("access_token")) {
            throw new IllegalStateException("Google no devolvió access_token");
        }
        cachedToken = response.get("access_token").asString();
        long expiresIn = response.hasNonNull("expires_in") ? response.get("expires_in").asLong() : 3600;
        cachedUntil = now.plusSeconds(Math.max(0, expiresIn - 60));
        return cachedToken;
    }

    static PrivateKey parsePrivateKey(String pem) {
        try {
            String base64 = pem
                    .replace("\\n", "\n")
                    .replace("-----BEGIN PRIVATE KEY-----", "")
                    .replace("-----END PRIVATE KEY-----", "")
                    .replaceAll("\\s", "");
            byte[] der = Base64.getDecoder().decode(base64);
            return KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(der));
        } catch (Exception ex) {
            throw new IllegalArgumentException("La clave privada de Firebase no es válida (se espera PKCS#8 PEM)", ex);
        }
    }
}
