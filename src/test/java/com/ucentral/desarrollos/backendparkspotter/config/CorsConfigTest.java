package com.ucentral.desarrollos.backendparkspotter.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.cors.CorsConfiguration;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Los orígenes del front aceptan comodines (vistas previas de Vercel o Netlify). */
class CorsConfigTest {

    @Test
    void acceptsExactOriginsAndWildcardPatterns() {
        CorsConfiguration config = new CorsConfig()
                .corsConfigurationSource(List.of("https://parkspotter.vercel.app", " https://*.netlify.app ", ""))
                .getCorsConfiguration(new MockHttpServletRequest("GET", "/api/v1/health"));
        assertThat(config).isNotNull();
        assertThat(config.checkOrigin("https://parkspotter.vercel.app")).isEqualTo("https://parkspotter.vercel.app");
        assertThat(config.checkOrigin("https://rama-123--parkspotter.netlify.app")).isNotNull();
        assertThat(config.checkOrigin("https://malicioso.com")).isNull();
        assertThat(config.getAllowCredentials()).isFalse();
    }
}
